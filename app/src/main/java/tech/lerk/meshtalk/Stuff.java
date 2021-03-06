package tech.lerk.meshtalk;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;

import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.providers.impl.ContactProvider;
import tech.lerk.meshtalk.providers.impl.IdentityProvider;

public class Stuff {
    private static final String TAG = Stuff.class.getCanonicalName();

    public static final String KEY_STORE_TYPE = "AndroidKeyStore";
    public static final String AES_MODE = "AES/GCM/NoPadding";
    public static final String APP_KEY_ALIAS = "MeshTalkKey";
    public static final String NONE = "NONE";

    public static void waitOrDonT(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.w(TAG, "Couldn't wait...", e);
        }
    }

    public static String getFingerprintForKey(RSAPublicKey publicKey) {
        try {
            byte[] n = publicKey.getModulus().toByteArray(); // Java is 2sC bigendian
            byte[] e = publicKey.getPublicExponent().toByteArray(); // and so is SSH
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(os);
            dout.writeInt(e.length);
            dout.write(e);
            dout.writeInt(n.length);
            dout.write(n);
            byte[] encoded = os.toByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA256");
            byte[] result = digest.digest(encoded);
            return Base64.getMimeEncoder().encodeToString(result);
        } catch (NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Unable to get fingerprint for key!", e);
            return "ERROR!";
        }
    }

    public static byte[] getReducedIV(byte[] deviceIV) {
        byte[] reducedIV = new byte[12];
        System.arraycopy(deviceIV, 0, reducedIV, 0, reducedIV.length);
        return reducedIV;
    }

    public static AlertDialog getLoadingDialog(MainActivity activity,
                                               @Nullable DialogInterface.OnClickListener negativeButtonListener) {
        return getLoadingDialog(activity, negativeButtonListener, R.string.loading);
    }

    public static AlertDialog getLoadingDialog(MainActivity activity,
                                               @Nullable DialogInterface.OnClickListener negativeButtonListener,
                                               @StringRes int titleRes) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(titleRes)
                .setView(R.layout.dialog_loading)
                .setCancelable(false);

        if (negativeButtonListener != null) {
            builder.setNegativeButton(R.string.action_back, negativeButtonListener);
        }
        return builder.create();
    }

    public static void getContactOrIdentityForId(@NonNull UUID id, @NonNull ContactProvider contactProvider,
                                                 @NonNull IdentityProvider identityProvider,
                                                 @NonNull Callback<Contact> callback) {
        contactProvider.getById(id, contactForId -> {
            if (contactForId != null) {
                callback.call(contactForId);
            } else {
                identityProvider.getById(id, identity -> {
                    if (identity != null) {
                        callback.call(identity);
                    } else {
                        callback.call(null);
                    }
                });
            }
        });
    }

    public static String ellipsize(int maxLength, @NonNull String text) {
        return (text.length() > maxLength) ? text.substring(0, maxLength - 1) + '\u2026' : text;
    }
}
