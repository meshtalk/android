package tech.lerk.meshtalk.providers;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class MessageProvider {
    private static MessageProvider instance = null;
    private final KeyProvider keyProvider;
    private final SharedPreferences preferences;

    private MessageProvider(Context context) {
        keyProvider = KeyProvider.get(context);
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static MessageProvider get(Context context) {
        if (instance == null) {
            instance = new MessageProvider(context);
        }
        return instance;
    }
}
