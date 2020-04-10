package tech.lerk.meshtalk.providers.impl;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.MessageGatewayClientService;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.db.entities.MessageDbo;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.providers.DatabaseProvider;

public class MessageProvider extends DatabaseProvider<Message> {
    private static final String TAG = MessageProvider.class.getCanonicalName();
    private static MessageProvider instance = null;
    private final IdentityProvider identityProvider;
    private final HandshakeProvider handshakeProvider;

    private MessageProvider(Context context) {
        super(context);
        identityProvider = IdentityProvider.get(context);
        handshakeProvider = HandshakeProvider.get(context);
    }

    public static MessageProvider get(Context context) {
        if (instance == null) {
            instance = new MessageProvider(context);
        }
        return instance;
    }

    @Override
    public void deleteAll() {
        AsyncTask.execute(() -> database.messageDao().deleteAll());
    }

    @Override
    public void getById(UUID id, @NonNull Callback<Message> callback) {
        AsyncTask.execute(() -> callback.call(DatabaseEntityConverter.convert(database.messageDao().getMessageById(id))));
    }

    @Override
    public void save(Message element) {
        AsyncTask.execute(() -> database.messageDao().insertMessage(DatabaseEntityConverter.convert(element)));
    }

    @Override
    public void deleteById(UUID id) {
        AsyncTask.execute(() -> database.messageDao().deleteMessageById(id));
    }

    @Override
    public void delete(Message element) {
        AsyncTask.execute(() -> database.messageDao().deleteMessage(DatabaseEntityConverter.convert(element)));
    }

    @Override
    public void exists(UUID id, @NonNull Callback<Boolean> callback) {
        AsyncTask.execute(() -> callback.call(database.messageDao().getMessages().stream().anyMatch(m -> m.getId().equals(id))));
    }

    @Override
    public void getAll(@NonNull Callback<Set<Message>> callback) {
        AsyncTask.execute(() -> callback.call(database.messageDao().getMessages().stream()
                .map(DatabaseEntityConverter::convert)
                .collect(Collectors.toCollection(TreeSet::new))));
    }

    public void getLatestMessage(Chat chat, Callback<Message> callback) {
        AsyncTask.execute(() -> {
            if (chat != null) {
                List<MessageDbo> messagesByChat = database.messageDao().getMessagesByChat(chat.getId());
                MessageDbo minMessage = messagesByChat.stream()
                        .max((o1, o2) -> o1.getDate().compareTo(o2.getDate()))
                        .orElse(null);
                callback.call(DatabaseEntityConverter.convert(minMessage));
            } else {
                callback.call(null);
            }
        });
    }

    public void decryptMessage(@NonNull String message, @NonNull Chat chat, @NonNull Callback<String> callback) {
        if (!message.isEmpty()) {
            handshakeProvider.getLatestByReceiver(chat.getSender(), handshake -> {
                if (handshake != null) {
                    identityProvider.getById(chat.getSender(), identity -> {
                        if (identity != null) {
                            SecretKey chatKey = MessageGatewayClientService.getSecretKeyFromHandshake(handshake, identity);
                            byte[] chatIv = MessageGatewayClientService.getIvFromHandshake(handshake, identity);
                            if (chatKey != null && chatIv != null) {
                                callback.call(decryptMessageSync(message, chatKey, chatIv));
                                return;
                            } else {
                                Log.e(TAG, "Chat key or iv is null!");
                            }
                            callback.call(null);
                        } else {
                            Log.e(TAG, "Identity is null!");
                            callback.call(null);
                        }
                    });
                } else {
                    throw new IllegalStateException("No handshake for chatId: '" + chat.getId() + "'");
                }
            });
        }
    }

    @Nullable
    public String decryptMessageSync(@NonNull String message, @NonNull SecretKey chatKey, @NonNull byte[] chatIv) {
        try {
            Cipher c = Cipher.getInstance(Stuff.AES_MODE);
            c.init(Cipher.DECRYPT_MODE, chatKey, new GCMParameterSpec(128, chatIv));
            byte[] decodedBytes = c.doFinal(Base64.getMimeDecoder().decode(message));
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            Log.wtf(TAG, "Unable to load cipher!", e);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "Unable to init cipher!", e);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "Unable to decrypt message!", e);
        }
        return null;
    }

    public void encryptMessage(@NonNull String message, @NonNull Chat chat, @NonNull Callback<String> callback) {
        if (!message.isEmpty()) {
            handshakeProvider.getLatestByReceiver(chat.getSender(), handshake -> {
                if (handshake != null) {
                    identityProvider.getById(chat.getSender(), identity -> {
                        if (identity != null) {
                            SecretKey chatKey = MessageGatewayClientService.getSecretKeyFromHandshake(handshake, identity);
                            byte[] chatIv = MessageGatewayClientService.getIvFromHandshake(handshake, identity);
                            try {
                                if (chatKey != null) {
                                    Cipher c = Cipher.getInstance(Stuff.AES_MODE);
                                    c.init(Cipher.ENCRYPT_MODE, chatKey, new GCMParameterSpec(128, chatIv));
                                    byte[] encodedBytes = c.doFinal(message.getBytes(StandardCharsets.UTF_8));
                                    callback.call(Base64.getMimeEncoder().encodeToString(encodedBytes));
                                } else {
                                    Log.e(TAG, "ChatKey is null!");
                                }
                            } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
                                Log.wtf(TAG, "Unable to load cipher!", e);
                            } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                                Log.e(TAG, "Unable to init cipher!", e);
                            } catch (BadPaddingException | IllegalBlockSizeException e) {
                                Log.e(TAG, "Unable to decrypt message!", e);
                            }
                        } else {
                            Log.e(TAG, "Identity is null!");
                        }
                    });
                } else {
                    throw new IllegalStateException("No handshake for chatId: '" + chat.getId() + "'");
                }
            });
        }
    }

    public void getByChat(@NonNull UUID chatId, @NonNull Callback<List<Message>> callback) {
        AsyncTask.execute(() ->
                callback.call(database.messageDao().getMessagesByChat(chatId).stream()
                        .map(DatabaseEntityConverter::convert).collect(Collectors.toList())));
    }

    public void deleteByChat(@NonNull UUID chatId) {
        AsyncTask.execute(() -> database.messageDao().deleteMessagesByChat(chatId));
    }

    public void getLiveMessagesByChat(@NonNull UUID chatId, Callback<LiveData<List<MessageDbo>>> callback) {
        AsyncTask.execute(() -> callback.call(database.messageDao().getLiveMessagesByChat(chatId)));
    }
}
