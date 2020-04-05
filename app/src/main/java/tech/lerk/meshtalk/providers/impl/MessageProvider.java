package tech.lerk.meshtalk.providers.impl;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
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
import tech.lerk.meshtalk.MessagesService;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.providers.DatabaseProvider;

public class MessageProvider extends DatabaseProvider<Message> {
    private static final String TAG = MessageProvider.class.getCanonicalName();
    private static MessageProvider instance = null;
    private final IdentityProvider identityProvider;

    private MessageProvider(Context context) {
        super(context);
        identityProvider = IdentityProvider.get(context);
    }

    @Override
    public void deleteAll() {
        database.messageDao().deleteAll();
    }

    public static MessageProvider get(Context context) {
        if (instance == null) {
            instance = new MessageProvider(context);
        }
        return instance;
    }

    @Override
    public void getById(UUID id, @NonNull Callback<Message> callback) {
        callback.call(DatabaseEntityConverter.convert(database.messageDao().getMessageById(id)));
    }

    @Override
    public void save(Message element) {
        database.messageDao().insertMessage(DatabaseEntityConverter.convert(element));
    }

    @Override
    public void deleteById(UUID id) {
        database.messageDao().deleteMessageById(id);
    }

    @Override
    public void delete(Message element) {
        database.messageDao().deleteMessage(DatabaseEntityConverter.convert(element));
    }

    @Override
    public void exists(UUID id, @NonNull Callback<Boolean> callback) {
        callback.call(database.messageDao().getMessages().stream().anyMatch(m -> m.getId().equals(id)));
    }

    @Override
    public void getAll(@NonNull Callback<Set<Message>> callback) {
        callback.call(database.messageDao().getMessages().stream()
                .map(DatabaseEntityConverter::convert)
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    public Message getLatestMessage(Chat chat) {
        if (chat != null && chat.getMessages() != null) {
            ArrayList<Message> messages = new ArrayList<>();
            chat.getMessages().forEach(i -> getById(i, messages::add));
            return messages.stream() //TODO it's very likely that this is now buggy!
                    .min((m1, m2) -> m1 != null ? m1.getDate().compareTo(m2.getDate()) : null)
                    .orElse(null);
        }
        return null;
    }

    public void decryptMessage(@NonNull String message, @NonNull Chat chat, @NonNull Callback<String> callback) {

        if (!message.isEmpty()) {
            Handshake handshake = chat.getHandshakes().get(chat.getSender());
            if (handshake != null) {
                try {
                    identityProvider.getById(chat.getSender(), identity -> {
                        if (identity != null) {
                            SecretKey chatKey = MessagesService.getSecretKeyFromHandshake(handshake, identity);
                            byte[] chatIv = MessagesService.getIvFromHandshake(handshake, identity);
                            try {
                                if (chatKey != null) {
                                    Cipher c = Cipher.getInstance(Stuff.AES_MODE);
                                    c.init(Cipher.DECRYPT_MODE, chatKey, new GCMParameterSpec(128, chatIv));
                                    byte[] decodedBytes = c.doFinal(Base64.getMimeDecoder().decode(message));
                                    callback.call(new String(decodedBytes, StandardCharsets.UTF_8));
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
                } catch (DecryptionException e) {
                    Log.e(TAG, "Unable to decrypt identity!", e);
                }
            } else {
                throw new IllegalStateException("No handshake for chatId: '" + chat.getId() + "'");
            }
        }
    }

    public void encryptMessage(@NonNull String message, @NonNull Chat chat, @NonNull Callback<String> callback) {
        if (!message.isEmpty()) {
            Handshake handshake = chat.getHandshakes().get(chat.getSender());
            if (handshake != null) {
                try {
                    identityProvider.getById(chat.getSender(), identity -> {
                        if (identity != null) {
                            SecretKey chatKey = MessagesService.getSecretKeyFromHandshake(handshake, identity);
                            byte[] chatIv = MessagesService.getIvFromHandshake(handshake, identity);
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
                } catch (DecryptionException e) {
                    Log.e(TAG, "Unable to decrypt identity!", e);
                }
            } else {
                throw new IllegalStateException("No handshake for chatId: '" + chat.getId() + "'");
            }
        }
    }
}
