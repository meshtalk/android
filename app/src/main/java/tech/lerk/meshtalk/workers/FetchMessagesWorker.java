package tech.lerk.meshtalk.workers;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
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

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.R;
import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.Preferences;
import tech.lerk.meshtalk.providers.impl.MessageProvider;

public class FetchMessagesWorker extends GatewayWorker {
    private static final String TAG = FetchMessagesWorker.class.getCanonicalName();

    public static final int ERROR_NOT_FOUND = 4;

    private final Gson gson;

    public FetchMessagesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        gson = Utils.getGson();
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
            Toast.makeText(getApplicationContext(), R.string.info_fetching_messages, Toast.LENGTH_SHORT).show();
            final String receiverUrl = gatewayInfo.toString() + "/messages/receiver/" + defaultdentityString;
            final String senderUrl = gatewayInfo.toString() + "/messages/sender/" + defaultdentityString;
            final boolean[] isDone = {false};
            final int[] code = {ERROR_INVALID_SETTINGS};
            MessageProvider messageProvider = MessageProvider.get(getApplicationContext());
            fetchMessages(receiverUrl, (recData, recErr) ->
                    fetchMessages(senderUrl, (sndData, sndErr) -> {
                        if (recErr != null && sndErr != null) {
                            if (sndData != null) {
                                sndData.forEach(messageProvider::save);
                            }
                            if (recData != null) {
                                recData.forEach(messageProvider::save);
                            }
                            code[0] = Math.max(sndErr, recErr);
                            isDone[0] = true;
                        } else {
                            Log.wtf(TAG, "If you see this in your log, good luck...");
                        }
                    }));
            while (!isDone[0]) {
                Thread.yield();
            }
            return ListenableWorker.Result.success(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), code[0]).build());
        }
        return ListenableWorker.Result.failure(new Data.Builder().putInt(DataKeys.ERROR_CODE.toString(), ERROR_INVALID_SETTINGS).build());
    }

    private void fetchMessages(String url, Callback.Multi<ArrayList<Message>, Integer> callback) {
        try {
            doMessageRequest(url, data -> {
                if (data != null) {
                    callback.call(data, ERROR_NONE);
                } else {
                    Log.e(TAG, "Unable to parse gateway response!");
                    callback.call(null, ERROR_PARSING);
                }
            });
        } catch (MalformedURLException e) {
            Log.e(TAG, "Unable to parse uri: '" + url + "'!", e);
            callback.call(null, ERROR_URI);
        } catch (FileNotFoundException e) {
            Log.i(TAG, "No messages found for: '" + url + "'!");
            callback.call(null, ERROR_NOT_FOUND);
        } catch (IOException e) {
            Log.e(TAG, "Unable to open connection to: '" + url + "'!", e);
            callback.call(null, ERROR_CONNECTION);
        }
    }

    private void doMessageRequest(String url, Callback<ArrayList<Message>> callback) throws IOException {
        URL gatewayMetaUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) gatewayMetaUrl.openConnection();
        if (connection.getResponseCode() == 200) {
            try (InputStream io = connection.getInputStream()) {
                callback.call(gson.fromJson(new JsonReader(new InputStreamReader(io)), getMessageListType()));
                return;
            } catch (JsonSyntaxException | JsonIOException e) {
                Log.w(TAG, "Unable to parse response of: '" + url + "'!", e);
            } finally {
                connection.disconnect();
            }
        } else if (connection.getResponseCode() == 404) {
            callback.call(new ArrayList<>());
            return;
        }
        callback.call(null);
    }

    private Type getMessageListType() {
        //@formatter:off
        return new TypeToken<ArrayList<Message>>() {}.getType();
        //@formatter:on
    }
}
