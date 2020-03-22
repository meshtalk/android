package tech.lerk.meshtalk.providers;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.KeyGenerator;

import tech.lerk.meshtalk.Stuff;

public class KeyProvider {
    private static final String TAG = KeyProvider.class.getCanonicalName();
    private KeyStore keyStore;
    private static KeyProvider instance = null;

    private KeyProvider() {
        try {
            keyStore = KeyStore.getInstance(Stuff.KEY_STORE_TYPE);
            keyStore.load(null);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            Log.wtf(TAG, "Unable to load keystore!", e);
        }
    }

    public static KeyProvider get() {
        if (instance == null) {
            instance = new KeyProvider();
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

    public Key getAppKey() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        return keyStore.getKey(Stuff.APP_KEY_ALIAS, null);
    }

    public void deleteAppKey() {
        try {
            keyStore.deleteEntry(Stuff.APP_KEY_ALIAS);
        } catch (KeyStoreException e) {
            Log.wtf(TAG, "Unable to delete app key!", e);
        }
    }
}
