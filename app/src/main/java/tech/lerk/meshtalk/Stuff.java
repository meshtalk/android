package tech.lerk.meshtalk;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

public class Stuff {
    private static final String TAG = Stuff.class.getCanonicalName();

    public static final String KEY_STORE_TYPE = "AndroidKeyStore";
    public static final String AES_MODE = "AES/GCM/NoPadding";
    public static final String APP_KEY_ALIAS = "MeshTalkKey";
    public static final String EMPTY_OBJECT = "{}";
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
            return Base64.encodeToString(result, Base64.DEFAULT);
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
}
