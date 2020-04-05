package tech.lerk.meshtalk;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleService;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.Gson;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.providers.Provider;
import tech.lerk.meshtalk.providers.impl.ChatProvider;
import tech.lerk.meshtalk.providers.impl.ContactProvider;
import tech.lerk.meshtalk.providers.impl.IdentityProvider;
import tech.lerk.meshtalk.providers.impl.MessageProvider;
import tech.lerk.meshtalk.workers.DataKeys;
import tech.lerk.meshtalk.workers.FetchHandshakesWorker;
import tech.lerk.meshtalk.workers.FetchMessagesWorker;
import tech.lerk.meshtalk.workers.SubmitHandshakeWorker;

public class MessagesService extends LifecycleService {
    private static final String TAG = MessagesService.class.getCanonicalName();
    private static final String FETCH_MESSAGES_TAG = "mt.fetch_messages";
    private static final String FETCH_HANDSHAKES_TAG = "mt.fetch_handshakes";

    private SharedPreferences preferences;
    private Gson gson;
    private KeyHolder keyHolder;
    private ChatProvider chatProvider;
    private ContactProvider contactProvider;
    private IdentityProvider identityProvider;

    private void startFetchMessageWorker() {
        OneTimeWorkRequest fetchMessagesRequest = new OneTimeWorkRequest
                .Builder(FetchMessagesWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager instance = WorkManager.getInstance(this);
        instance.enqueue(fetchMessagesRequest);
        instance.getWorkInfoByIdLiveData(fetchMessagesRequest.getId()).observe(this, info -> {
            if (info != null && info.getState().isFinished()) {
                Data data = info.getOutputData();
                int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                switch (errorCode) {
                    case FetchMessagesWorker.ERROR_INVALID_SETTINGS:
                    case FetchMessagesWorker.ERROR_URI:
                    case FetchMessagesWorker.ERROR_CONNECTION:
                    case FetchMessagesWorker.ERROR_PARSING:
                        String msg = "Got error " + errorCode + " while fetching messages!";
                        Log.e(TAG, msg);
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        break;
                    case FetchMessagesWorker.ERROR_NOT_FOUND:
                        Log.i(TAG, "No messages found on gateway.");
                        break;
                    case FetchMessagesWorker.ERROR_NONE:
                        AsyncTask.execute(() -> {
                            for (int i = 0; i < data.getInt(DataKeys.MESSAGE_LIST_SIZE.toString(), 0); i++) {
                                tech.lerk.meshtalk.entities.Message message = gson.fromJson(data.getString(DataKeys.MESSAGE_LIST_ELEMENT_PREFIX + String.valueOf(i)), tech.lerk.meshtalk.entities.Message.class);
                                MessageProvider.get(getApplicationContext()).save(message);
                            }
                            MainActivity.maybeUpdateViews();
                        });
                        break;
                    default:
                        Log.wtf(TAG, "Invalid error code: " + errorCode + "!");
                        break;
                }
            }
        });
    }

    private void handleHandshake(Handshake handshake) {
        Log.i(TAG, "Handshake received from: '" + handshake.getSender() + "'!");
        chatProvider.getById(handshake.getId(), c ->
                getChatName(handshake.getSender(), chatName -> {
                    Chat chat = c;
                    if (chat == null) {
                        // new chat "request"
                        //TODO add ability to decline a chat (?)
                        chat = new Chat();
                        chat.setId(handshake.getChat());
                        chat.setRecipient(handshake.getSender());
                        chat.setSender(handshake.getReceiver());
                        chat.setTitle(chatName);
                        chat.setHandshakes(new HashMap<>());
                        chat.setMessages(new TreeSet<>());
                    }
                    HashMap<UUID, Handshake> handshakes = chat.getHandshakes();

                    Handshake maybeExistingHandshake = handshakes.get(handshake.getReceiver());
                    if (maybeExistingHandshake != null && maybeExistingHandshake.getId().equals(handshake.getId())) {
                        return; // we already have this one.
                    }

                    handshakes.put(handshake.getReceiver(), handshake);
                    if (handshakes.get(handshake.getSender()) == null) { // if we haven't sent a handshake yet...
                        replyToHandshake(handshake, chat);
                    }
                    chat.setHandshakes(handshakes);
                    chatProvider.save(chat);
                    MainActivity.maybeUpdateViews();
                }));
    }

    private void replyToHandshake(Handshake handshake, Chat chat) {
        try {
            identityProvider.getById(handshake.getReceiver(), handshakeIdentity -> {
                if (handshakeIdentity != null) {
                    SecretKey secretKey = getSecretKey(handshake, handshakeIdentity);
                    if (secretKey != null) {
                        try {
                            Cipher cipher = Cipher.getInstance("RSA");
                            contactProvider.getById(handshake.getSender(), contactById -> {
                                try {
                                    if (contactById != null) {
                                        cipher.init(Cipher.ENCRYPT_MODE, contactById.getPublicKey());

                                        Handshake handshakeReply = new Handshake();
                                        handshakeReply.setId(UUID.randomUUID());
                                        handshakeReply.setChat(chat.getId());
                                        handshakeReply.setReceiver(chat.getRecipient());
                                        handshakeReply.setSender(chat.getSender());
                                        handshakeReply.setKey(Base64.getMimeEncoder().encodeToString(cipher.doFinal(secretKey.getEncoded())));
                                        handshakeReply.setDate(LocalDateTime.now());

                                        HashMap<UUID, Handshake> handshakes1 = chat.getHandshakes();
                                        handshakes1.put(chat.getRecipient(), handshakeReply);
                                        chat.setHandshakes(handshakes1);
                                        chatProvider.save(chat);

                                        Data handshakeData = new Data.Builder()
                                                .putString(DataKeys.HANDSHAKE_ID.toString(), handshakeReply.getId().toString())
                                                .putString(DataKeys.HANDSHAKE_CHAT.toString(), handshakeReply.getChat().toString())
                                                .putString(DataKeys.HANDSHAKE_SENDER.toString(), handshakeReply.getSender().toString())
                                                .putString(DataKeys.HANDSHAKE_RECEIVER.toString(), handshakeReply.getReceiver().toString())
                                                .putString(DataKeys.HANDSHAKE_DATE.toString(), handshakeReply.getDate().toString())
                                                .putString(DataKeys.HANDSHAKE_KEY.toString(), handshakeReply.getKey())
                                                .build();

                                        OneTimeWorkRequest sendHandshakeWorkRequest = new OneTimeWorkRequest.Builder(SubmitHandshakeWorker.class)
                                                .setInputData(handshakeData).build();
                                        WorkManager workManager = WorkManager.getInstance(this);
                                        workManager.enqueue(sendHandshakeWorkRequest);

                                        MainActivity.maybeRunOnUiThread(() ->
                                                workManager.getWorkInfoByIdLiveData(sendHandshakeWorkRequest.getId()).observe(this, info -> {
                                                    if (info != null && info.getState().isFinished()) {
                                                        Data data = info.getOutputData();
                                                        int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                                                        switch (errorCode) {
                                                            case SubmitHandshakeWorker.ERROR_INVALID_SETTINGS:
                                                            case SubmitHandshakeWorker.ERROR_URI:
                                                            case SubmitHandshakeWorker.ERROR_CONNECTION:
                                                            case SubmitHandshakeWorker.ERROR_PARSING:
                                                                String msg = "Got error " + errorCode + " while sending handshake!";
                                                                Log.e(TAG, msg);
                                                                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                                                                break;
                                                            case SubmitHandshakeWorker.ERROR_NONE:
                                                            default:
                                                                Toast.makeText(getApplicationContext(), R.string.success_sending_handshake, Toast.LENGTH_LONG).show();
                                                                break;
                                                        }
                                                    }
                                                })
                                        );
                                    } else {
                                        Log.e(TAG, "Contact is null!");
                                        MainActivity.maybeRunOnUiThread(() -> Toast.makeText(getApplicationContext(),
                                                R.string.error_unknown_contact, Toast.LENGTH_LONG).show());
                                    }
                                } catch (InvalidKeyException e) {
                                    Log.e(TAG, "Unable to init Cipher!", e);
                                } catch (BadPaddingException | IllegalBlockSizeException e) {
                                    Log.e(TAG, "Unable to encrypt handshake!", e);
                                }
                            });
                        } catch (NoSuchAlgorithmException e) {
                            Log.wtf(TAG, "Unable to get KeyGenerator!", e);
                        } catch (NoSuchPaddingException e) {
                            Log.wtf(TAG, "Unable to get Cipher!", e);
                        }
                    } else {
                        String msg = "Secret key is null!";
                        Log.e(TAG, msg);
                        MainActivity.maybeRunOnUiThread(() -> Toast.makeText(getApplicationContext(),
                                msg, Toast.LENGTH_LONG).show());
                    }
                } else {
                    String msg = "Identity is null!";
                    Log.e(TAG, msg);
                    MainActivity.maybeRunOnUiThread(() -> Toast.makeText(getApplicationContext(),
                            msg, Toast.LENGTH_LONG).show());
                }
            });
        } catch (DecryptionException e) {
            Log.e(TAG, "Unable to decrypt identity!", e);
        }
    }

    private void startFetchHandshakeWorker() {
        OneTimeWorkRequest fetchHandshakesRequest = new OneTimeWorkRequest
                .Builder(FetchHandshakesWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager instance = WorkManager.getInstance(this);
        instance.enqueue(fetchHandshakesRequest);
        instance.getWorkInfoByIdLiveData(fetchHandshakesRequest.getId()).observe(this, info -> {
            if (info != null && info.getState().isFinished()) {
                Data data = info.getOutputData();
                int errorCode = data.getInt(DataKeys.ERROR_CODE.toString(), -1);
                switch (errorCode) {
                    case FetchHandshakesWorker.ERROR_INVALID_SETTINGS:
                    case FetchHandshakesWorker.ERROR_URI:
                    case FetchHandshakesWorker.ERROR_CONNECTION:
                    case FetchHandshakesWorker.ERROR_PARSING:
                        String msg = "Got error " + errorCode + " while fetching handshakes!";
                        Log.e(TAG, msg);
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        break;
                    case FetchHandshakesWorker.ERROR_NOT_FOUND:
                    case FetchHandshakesWorker.ERROR_NONE:
                        if (data.getInt(DataKeys.HANDSHAKE_LIST_SIZE.toString(), 0) > 0) {
                            parseHandshakeResponse(data);
                        }
                        break;
                    default:
                        Log.wtf(TAG, "Invalid error code: " + errorCode + "!");
                        break;
                }
            }
        });
    }

    private void parseHandshakeResponse(Data data) {
        AsyncTask.execute(() -> {
            for (int i = 0; i < data.getInt(DataKeys.HANDSHAKE_LIST_SIZE.toString(), 0); i++) {
                Handshake handshake = gson.fromJson(data.getString(DataKeys.HANDSHAKE_LIST_ELEMENT_PREFIX + String.valueOf(i)), Handshake.class);
                handleHandshake(handshake);
            }
        });
    }

    @Nullable
    private SecretKey getSecretKey(Handshake handshake, Identity identity) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, identity.getPrivateKey());
            byte[] decryptedKey = cipher.doFinal(Base64.getMimeDecoder().decode(handshake.getKey()));
            return new SecretKeySpec(decryptedKey, "AES");
        } catch (NoSuchAlgorithmException | InvalidKeyException |
                NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "Unable to decrypt handshake '" + handshake.getId() + "' with identity '" + identity.getId() + "'");
        }
        return null;
    }

    private void getChatName(UUID sender, Provider.LookupCallback<String> callback) {
        String title = "Chat with ";
        contactProvider.getById(sender, contact -> {
            if (contact == null) {
                callback.call(title + sender);
            } else {
                callback.call(title + contact.getName());
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gson = Utils.getGson();
        keyHolder = KeyHolder.get(getApplicationContext());
        chatProvider = ChatProvider.get(getApplicationContext());
        contactProvider = ContactProvider.get(getApplicationContext());
        identityProvider = IdentityProvider.get(getApplicationContext());
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();

        boolean useMessageGateway = preferences.getBoolean(Preferences.USE_MESSAGE_GATEWAY.toString(), true);

        if (useMessageGateway) {
            startFetchHandshakeWorker();
            startFetchMessageWorker();
        } else {
            //TODO: implement
        }

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }


    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
    }
}
