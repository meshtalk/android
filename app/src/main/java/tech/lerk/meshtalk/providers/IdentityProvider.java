package tech.lerk.meshtalk.providers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;

import tech.lerk.meshtalk.KeyHolder;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.adapters.PrivateKeyTypeAdapter;
import tech.lerk.meshtalk.adapters.PublicKeyTypeAdapter;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.Preferences;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.exceptions.EncryptionException;

public class IdentityProvider implements Provider<Identity> {
    private static final String TAG = IdentityProvider.class.getCanonicalName();
    private static IdentityProvider instance = null;
    private final KeyHolder keyHolder;
    private final SharedPreferences preferences;
    private static final String identityPrefix = "IDENTITY_";
    private final Gson gson;

    private IdentityProvider(Context context) {
        keyHolder = KeyHolder.get(context);
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        gson = new GsonBuilder()
                .registerTypeAdapter(PrivateKey.class, new PrivateKeyTypeAdapter())
                .registerTypeAdapter(PublicKey.class, new PublicKeyTypeAdapter())
                .create();
    }

    public static IdentityProvider get(Context context) {
        if (instance == null) {
            instance = new IdentityProvider(context);
        }
        return instance;
    }

    @Override
    public Identity getById(UUID id) throws DecryptionException {
        try {
            String encryptedIdentity = preferences.getString(identityPrefix + id, Stuff.EMPTY_OBJECT);
            Cipher c = Cipher.getInstance(Stuff.AES_MODE);
            c.init(Cipher.DECRYPT_MODE, keyHolder.getAppKey(), new GCMParameterSpec(128, keyHolder.getDeviceIV()));
            byte[] decodedBytes = c.doFinal(Base64.getMimeDecoder().decode(encryptedIdentity));
            String decryptedJson = new String(decodedBytes, StandardCharsets.UTF_8);
            try {
                return gson.fromJson(decryptedJson, Identity.class);
            } catch (JsonSyntaxException e) {
                Log.w(TAG, "Unable to parse identity!", e);
                return null;
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                InvalidAlgorithmParameterException | InvalidKeyException |
                IllegalBlockSizeException | BadPaddingException e) {
            throw new DecryptionException("Unable to decrypt identity: '" + id.toString() + "'!", e);
        }
    }

    @Override
    public void save(Identity identity) throws EncryptionException {
        try {
            String json = gson.toJson(identity);
            Cipher c = Cipher.getInstance(Stuff.AES_MODE);
            c.init(Cipher.ENCRYPT_MODE, keyHolder.getAppKey(), new GCMParameterSpec(128, keyHolder.getDeviceIV()));
            byte[] encodedBytes = c.doFinal(json.getBytes(StandardCharsets.UTF_8));
            String encryptedBase64Encoded = Base64.getMimeEncoder().encodeToString(encodedBytes);
            Set<String> identities = preferences.getStringSet(Preferences.IDENTITIES.toString(), new TreeSet<>());
            identities.add(identity.getId().toString());
            preferences.edit()
                    .putString(identityPrefix + identity.getId().toString(), encryptedBase64Encoded)
                    .putStringSet(Preferences.IDENTITIES.toString(), identities)
                    .apply();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException | IllegalBlockSizeException |
                BadPaddingException e) {
            throw new EncryptionException("Unable to encrypt identity: '" + identity.getId().toString() + "'!", e);
        }
    }

    @Override
    public Set<UUID> getAllIds() {
        return preferences.getStringSet(Preferences.IDENTITIES.toString(), new TreeSet<>())
                .stream().map(UUID::fromString).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public void deleteById(UUID id) {
        Set<String> identities = preferences.getStringSet(Preferences.IDENTITIES.toString(), new TreeSet<>());
        identities.remove(id.toString());
        preferences.edit()
                .remove(identityPrefix + id)
                .putStringSet(Preferences.IDENTITIES.toString(), identities)
                .apply();
    }

    @Override
    public boolean exists(UUID id) {
        return preferences.getString(identityPrefix + id, null) != null;
    }
}
