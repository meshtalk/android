package tech.lerk.meshtalk.providers.impl;

import android.content.Context;
import android.util.Log;

import java.nio.charset.StandardCharsets;
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

import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.db.MessageDbo;
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

    public static MessageProvider get(Context context) {
        if (instance == null) {
            instance = new MessageProvider(context);
        }
        return instance;
    }

    @Override
    public void getById(UUID id, LookupCallback<Message> callback) {
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
    public void exists(UUID id, LookupCallback<Boolean> callback) {
        callback.call(database.messageDao().getMessages().stream().anyMatch(m -> m.getId().equals(id)));
    }

    @Override
    public void getAllIds(LookupCallback<Set<UUID>> callback) {
        callback.call(database.messageDao().getMessages().stream()
                .map(MessageDbo::getId).collect(Collectors.toCollection(TreeSet::new)));
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

    public void decryptMessage(Message message, Chat chat, LookupCallback<String> callback) {
        if (message != null && chat != null) {

            if (message.getSender().equals(chat.getRecipient())) {
                try {
                    identityProvider.getById(message.getReceiver(), identity -> {
                        try {
                            if (identity != null) {
                                Cipher cipher = Cipher.getInstance("RSA");
                                cipher.init(Cipher.DECRYPT_MODE, identity.getPrivateKey());
                                callback.call(new String(cipher.doFinal(Base64.getMimeDecoder().decode(message.getContent())), StandardCharsets.UTF_8));
                            } else {
                                Log.e(TAG, "Identity is null!");
                            }
                        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
                            Log.wtf(TAG, "Unable to load cipher!", e);
                        } catch (InvalidKeyException e) {
                            Log.e(TAG, "Unable to init cipher!", e);
                        } catch (BadPaddingException | IllegalBlockSizeException e) {
                            Log.e(TAG, "Unable to decrypt message!", e);
                        }
                    });
                } catch (DecryptionException e) {
                    Log.e(TAG, "Unable to decrypt identity!", e);
                }
            } else {
                callback.call(message.getContent());
            }
        }
    }
}
