package tech.lerk.meshtalk;

import android.util.Log;

public class Stuff {
    private static final String TAG = Stuff.class.getCanonicalName();

    public static final String KEY_STORE_TYPE = "AndroidKeyStore";
    public static final String AES_MODE = "AES/GCM/NoPadding";
    public static final String APP_KEY_ALIAS = "MeshTalkKey";

    public static void waitOrDonT(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.w(TAG, "Couldn't wait...", e);
        }
    }
}
