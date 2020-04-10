package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.MainActivity;
import tech.lerk.meshtalk.Preferences;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.providers.impl.ChatProvider;
import tech.lerk.meshtalk.providers.impl.ContactProvider;
import tech.lerk.meshtalk.providers.impl.HandshakeProvider;

public class FetchHandshakesWorker extends GatewayWorker {
    private static final String TAG = FetchHandshakesWorker.class.getCanonicalName();

    public static final int ERROR_NOT_FOUND = 4;

    private final Gson gson;
    private final ChatProvider chatProvider;
    private final ContactProvider contactProvider;
    private final HandshakeProvider handshakeProvider;

    public FetchHandshakesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        gson = Utils.getGson();
        chatProvider = ChatProvider.get(getApplicationContext());
        contactProvider = ContactProvider.get(getApplicationContext());
        handshakeProvider = HandshakeProvider.get(getApplicationContext());
    }

    @NonNull
    @Override
    public Result doWork() {
        GatewayInfo gatewayInfo = getGatewayInfo();
        String defaultdentityString = preferences.getString(Preferences.DEFAULT_IDENTITY.toString(), null);
        if (defaultdentityString != null) {
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            Toast.makeText(getApplicationContext(), R.string.info_fetching_handshakes, Toast.LENGTH_SHORT).show();

            ArrayList<Handshake> handshakes = new ArrayList<>();

            Pair<Integer, ArrayList<Handshake>> senderPair = fetchHandshakesSender(defaultdentityString, gatewayInfo);
            Pair<Integer, ArrayList<Handshake>> receiverPair = fetchHandshakesReceiver(defaultdentityString, gatewayInfo);

            Integer receiverRes = receiverPair.first;
            Integer senderRes = senderPair.first;
            if (!senderRes.equals(receiverRes)) {
                Log.w(TAG, "Request results not the same, potential error might be skipped!");
            }

            handshakes.addAll(senderPair.second);
            handshakes.addAll(receiverPair.second);

            handshakes.forEach(this::handleHandshake);
            return ListenableWorker.Result.success(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), ERROR_NONE).build());
        }
        return ListenableWorker.Result.failure(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), ERROR_INVALID_SETTINGS).build());
    }

    private void handleHandshake(Handshake handshake) {

        Log.i(TAG, "Handshake received from: '" + handshake.getSender() + "'!");
        handshakeProvider.exists(handshake.getId(), knownHandshake -> {
            if (knownHandshake == null || !knownHandshake) {
                chatProvider.getById(handshake.getChat(), chat -> {
                    if (chat == null) { // new chat
                        //TODO add ability to decline a chat (?)
                        getChatName(handshake.getSender(), chatName -> {
                            Chat newChat = new Chat();
                            newChat.setId(handshake.getChat());
                            newChat.setRecipient(handshake.getSender());
                            newChat.setSender(handshake.getReceiver());
                            newChat.setTitle(chatName);
                            newChat.setHandshake(handshake.getId());
                            saveAndUpdate(handshake, newChat);
                        });
                    } else {
                        handshakeProvider.getById(chat.getHandshake(), chatHandshake -> {
                            if (chatHandshake == null || chatHandshake.getDate().isBefore(handshake.getDate())) {
                                chat.setHandshake(handshake.getId());
                            }
                            saveAndUpdate(handshake, chat);
                        });
                    }
                });
            }
        });
    }

    private void getChatName(UUID sender, Callback<String> callback) {
        String title = "Chat with ";
        contactProvider.getById(sender, contact -> {
            if (contact == null) {
                callback.call(title + sender);
            } else {
                callback.call(title + contact.getName());
            }
        });
    }

    private void saveAndUpdate(Handshake handshake, Chat newChat) {
        handshakeProvider.save(handshake);
        chatProvider.save(newChat);
        MainActivity.maybeUpdateViews();
    }

    private Pair<Integer, ArrayList<Handshake>> fetchHandshakesSender(String identityId, GatewayInfo gatewayInfo) {
        try {
            URL gatewayMetaUrl = new URL(gatewayInfo.toString() + "/handshakes/sender/" + identityId);
            HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
            try (InputStream io = connection.getInputStream()) {
                return new Pair<>(ERROR_NONE, gson.fromJson(new JsonReader(new InputStreamReader(io)), getHandshakeListType()));
            } catch (JsonSyntaxException | JsonIOException e) {
                Log.e(TAG, "Unable to parse gateway response!", e);
                return new Pair<>(ERROR_PARSING, new ArrayList<>());
            } finally {
                connection.disconnect();
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, "Unable to parse uri!", e);
            return new Pair<>(ERROR_URI, new ArrayList<>());
        } catch (FileNotFoundException e) {
            Log.i(TAG, "No handshakes found for: '" + identityId + "'!");
            return new Pair<>(ERROR_NOT_FOUND, new ArrayList<>());
        } catch (IOException e) {
            Log.e(TAG, "Unable to open connection!", e);
            return new Pair<>(ERROR_CONNECTION, new ArrayList<>());
        }
    }

    private Pair<Integer, ArrayList<Handshake>> fetchHandshakesReceiver(String identityId, GatewayInfo gatewayInfo) {
        try {
            URL gatewayMetaUrl = new URL(gatewayInfo.toString() + "/handshakes/receiver/" + identityId);
            HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
            try (InputStream io = connection.getInputStream()) {
                return new Pair<>(ERROR_NONE, gson.fromJson(new JsonReader(new InputStreamReader(io)), getHandshakeListType()));
            } catch (JsonSyntaxException | JsonIOException e) {
                Log.e(TAG, "Unable to parse gateway response!", e);
                return new Pair<>(ERROR_PARSING, new ArrayList<>());
            } finally {
                connection.disconnect();
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, "Unable to parse uri!", e);
            return new Pair<>(ERROR_URI, new ArrayList<>());
        } catch (FileNotFoundException e) {
            Log.i(TAG, "No handshakes found for: '" + identityId + "'!");
            return new Pair<>(ERROR_NOT_FOUND, new ArrayList<>());
        } catch (IOException e) {
            Log.e(TAG, "Unable to open connection!", e);
            return new Pair<>(ERROR_CONNECTION, new ArrayList<>());
        }
    }

    private Type getHandshakeListType() {
        //@formatter:off
        return new TypeToken<ArrayList<Handshake>>() {}.getType();
        //@formatter:on
    }
}
