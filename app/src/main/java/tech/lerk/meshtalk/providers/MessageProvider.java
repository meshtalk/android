package tech.lerk.meshtalk.providers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.exceptions.DecryptionException;

public class MessageProvider implements Provider<Message> {
    private static final String TAG = MessageProvider.class.getCanonicalName();
    private static MessageProvider instance = null;
    private final SharedPreferences preferences;
    private static final String messagePrefix = "MESSAGE_";
    private final Gson gson;
    private final IdentityProvider identityProvider;

    private MessageProvider(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        identityProvider = IdentityProvider.get(context);
        gson = Utils.getGson();
    }

    public static MessageProvider get(Context context) {
        if (instance == null) {
            instance = new MessageProvider(context);
        }
        return instance;
    }

    @Nullable
    @Override
    public Message getById(UUID id) {
        return gson.fromJson(preferences.getString(messagePrefix + id, Stuff.EMPTY_OBJECT), Message.class);
    }

    @Override
    public void save(Message element) {
        Set<String> messages = preferences.getStringSet(Preferences.MESSAGES.toString(), new TreeSet<>());
        messages.add(element.getId().toString());
        preferences.edit()
                .putString(messagePrefix + element.getId(), gson.toJson(element))
                .putStringSet(Preferences.MESSAGES.toString(), messages)
                .apply();
    }

    @Override
    public void deleteById(UUID id) {
        Set<String> messages = preferences.getStringSet(Preferences.MESSAGES.toString(), new TreeSet<>());
        messages.remove(id.toString());
        preferences.edit()
                .remove(messagePrefix + id)
                .putStringSet(Preferences.MESSAGES.toString(), messages)
                .apply();
    }

    @Override
    public boolean exists(UUID id) {
        return preferences.getString(messagePrefix + id, null) != null;
    }

    @NonNull
    @Override
    public Set<UUID> getAllIds() {
        return preferences.getStringSet(Preferences.MESSAGES.toString(), new TreeSet<>()).stream()
                .map(UUID::fromString).collect(Collectors.toCollection(TreeSet::new));
    }

    public Message getLatestMessage(Chat chat) {
        if (chat != null && chat.getMessages() != null) {
            Optional<Message> latestMessage = chat.getMessages().stream()
                    .map(this::getById)
                    .min((m1, m2) -> m1 != null ? m1.getDate().compareTo(m2.getDate()) : null);
            return latestMessage.orElse(null);
        }
        return null;
    }

    public String decryptMessage(Message message, Chat chat) {
        if (message != null && chat != null) {
            try {
                if (message.getSender().equals(chat.getRecipient())) {
                    Identity identity = identityProvider.getById(message.getReceiver());
                    if (identity != null) {
                        Cipher cipher = Cipher.getInstance("RSA");
                        cipher.init(Cipher.DECRYPT_MODE, identity.getPrivateKey());
                        return new String(cipher.doFinal(Base64.getMimeDecoder().decode(message.getContent())), StandardCharsets.UTF_8);
                    } else {
                        Log.e(TAG, "Identity is null!");
                    }
                } else {
                    return message.getContent();
                }
            } catch (DecryptionException e) {
                Log.e(TAG, "Unable to decrypt identity!", e);
            } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
                Log.wtf(TAG, "Unable to load cipher!", e);
            } catch (InvalidKeyException e) {
                Log.e(TAG, "Unable to init cipher!", e);
            } catch (BadPaddingException | IllegalBlockSizeException e) {
                Log.e(TAG, "Unable to decrypt message!", e);
            }
        }
        return null;
    }
}
