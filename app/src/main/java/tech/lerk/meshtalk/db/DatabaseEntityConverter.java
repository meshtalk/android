package tech.lerk.meshtalk.db;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonSyntaxException;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;

import tech.lerk.meshtalk.KeyHolder;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.db.ChatDbo;
import tech.lerk.meshtalk.entities.db.ContactDbo;
import tech.lerk.meshtalk.entities.db.IdentityDbo;
import tech.lerk.meshtalk.entities.db.MessageDbo;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.exceptions.EncryptionException;

public final class DatabaseEntityConverter {

    private static final String TAG = DatabaseEntityConverter.class.getCanonicalName();

    private DatabaseEntityConverter() {
    }

    public static Message convert(@NonNull MessageDbo m) {
        Message m1 = new Message();
        m1.setId(m.getId());
        m1.setDate(m.getDate());
        m1.setContent(m.getContent());
        m1.setChat(m.getChat());
        m1.setReceiver(m.getReceiver());
        m1.setSender(m.getSender());
        return m1;
    }

    public static MessageDbo convert(@NonNull Message m) {
        return new MessageDbo(m.getId(), m.getSender(), m.getReceiver(), m.getDate(), m.getChat(), m.getContent());
    }

    public static Identity convert(@NonNull IdentityDbo i, KeyHolder keyHolder) throws DecryptionException {
        try {
            String encryptedIdentity = i.getEncrypted();
            Cipher c = Cipher.getInstance(Stuff.AES_MODE);
            c.init(Cipher.DECRYPT_MODE, keyHolder.getAppKey(), new GCMParameterSpec(128, keyHolder.getDeviceIV()));
            byte[] decodedBytes = c.doFinal(Base64.getMimeDecoder().decode(encryptedIdentity));
            String decryptedJson = new String(decodedBytes, StandardCharsets.UTF_8);
            try {
                Identity identity = Utils.getGson().fromJson(decryptedJson, Identity.class);
                if (!i.getId().equals(identity.getId())) {
                    String message = "IdentityIDs don't match! i: '" + i.getId() +
                            "', decoded: '" + identity.getId() + "'";
                    throw new IllegalStateException(message);
                }
                return identity;
            } catch (JsonSyntaxException e) {
                Log.w(TAG, "Unable to parse identity!", e);
                return null;
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                InvalidAlgorithmParameterException | InvalidKeyException |
                IllegalBlockSizeException | BadPaddingException e) {
            throw new DecryptionException("Unable to decrypt identity: '" + i.getId() + "'!", e);
        }
    }

    public static IdentityDbo convert(@NonNull Identity i, KeyHolder keyHolder) throws EncryptionException {
        try {
            String json = Utils.getGson().toJson(i);
            Cipher c = Cipher.getInstance(Stuff.AES_MODE);
            c.init(Cipher.ENCRYPT_MODE, keyHolder.getAppKey(), new GCMParameterSpec(128, keyHolder.getDeviceIV()));
            byte[] encodedBytes = c.doFinal(json.getBytes(StandardCharsets.UTF_8));
            String encryptedBase64Encoded = Base64.getMimeEncoder().encodeToString(encodedBytes);
            return new IdentityDbo(i.getId(), encryptedBase64Encoded);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException | IllegalBlockSizeException |
                BadPaddingException e) {
            throw new EncryptionException("Unable to encrypt identity: '" + i.getId().toString() + "'!", e);
        }
    }

    public static Contact convert(@NonNull ContactDbo c) {
        Contact c1 = new Contact();
        c1.setId(c.getId());
        c1.setName(c.getName());
        c1.setPublicKey(c.getPublicKeyObj());
        return c1;
    }

    public static ContactDbo convert(@NonNull Contact c) {
        return new ContactDbo(c.getId(), c.getName(), Utils.getGson().toJson(c.getPublicKey()));
    }

    public static Chat convert(@NonNull ChatDbo c) {
        Chat c1 = new Chat();
        c1.setId(c.getId());
        c1.setRecipient(c.getRecipient());
        c1.setSender(c.getSender());
        c1.setTitle(c.getTitle());
        c1.setMessages(c.getMessages());
        c1.setHandshakes(c.getHandshakes());
        return c1;
    }

    public static ChatDbo convert(@NonNull Chat c) {
        return new ChatDbo(c.getId(), c.getTitle(), c.getRecipient(), c.getSender(), c.getMessages(), c.getHandshakes());
    }
}
