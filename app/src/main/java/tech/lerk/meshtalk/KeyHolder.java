package tech.lerk.meshtalk;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Objects;

import javax.crypto.KeyGenerator;

import tech.lerk.meshtalk.entities.Preferences;

public class KeyHolder {
    private static final String TAG = KeyHolder.class.getCanonicalName();
    private KeyStore keyStore;
    private static KeyHolder instance = null;
    private final SharedPreferences preferences;

    private KeyHolder(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        try {
            keyStore = KeyStore.getInstance(Stuff.KEY_STORE_TYPE);
            keyStore.load(null);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            Log.wtf(TAG, "Unable to load keystore!", e);
        }
    }

    public static KeyHolder get(Context context) {
        if (instance == null) {
            instance = new KeyHolder(context);
        }
        return instance;
    }

    public boolean keyExists(String keyAlias) {
        try {
            return keyStore.containsAlias(Stuff.APP_KEY_ALIAS);
        } catch (KeyStoreException e) {
            Log.wtf(TAG, "Keystore not initialized!", e);
            return false;
        }
    }

    public void generateAppKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Stuff.KEY_STORE_TYPE);
            keyGenerator.init(new KeyGenParameterSpec.Builder(Stuff.APP_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(false)
                    .build());
            keyGenerator.generateKey();
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            Log.wtf(TAG, "Unable to generate app key!", e);
        }
    }

    public Key getAppKey() {
        try {
            return keyStore.getKey(Stuff.APP_KEY_ALIAS, null);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            Log.wtf(TAG, "Unable to get app key!", e);
            return null;
        }
    }

    public void deleteAppKey() {
        try {
            keyStore.deleteEntry(Stuff.APP_KEY_ALIAS);
        } catch (KeyStoreException e) {
            Log.wtf(TAG, "Unable to delete app key!", e);
        }
    }

    public byte[] getDeviceIV() {
        String encodedIV = preferences.getString(Preferences.DEVICE_IV.toString(), null);
        byte[] deviceIV = Objects.requireNonNull(encodedIV).getBytes(StandardCharsets.UTF_8);
        byte[] reducedIV = new byte[12];
        System.arraycopy(deviceIV, 0, reducedIV, 0, reducedIV.length);
        return reducedIV;
    }
}
