package tech.lerk.meshtalk.providers;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.UUID;

import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.adapters.PrivateKeyTypeAdapter;
import tech.lerk.meshtalk.adapters.PublicKeyTypeAdapter;
import tech.lerk.meshtalk.entities.Contact;

public class ContactProvider {
    private static ContactProvider instance = null;
    private final SharedPreferences preferences;
    private static final String contactsPrefix = "CONTACT_";
    private final Gson gson;

    private ContactProvider(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        gson = new GsonBuilder()
                .registerTypeAdapter(PrivateKey.class, new PrivateKeyTypeAdapter())
                .registerTypeAdapter(PublicKey.class, new PublicKeyTypeAdapter())
                .create();
    }

    public static ContactProvider get(Context context) {
        if (instance == null) {
            instance = new ContactProvider(context);
        }
        return instance;
    }

    public Contact getById(UUID id) {
        String contactJson = preferences.getString(contactsPrefix + id.toString(), Stuff.EMPTY_OBJECT);
        return gson.fromJson(contactJson, Contact.class);
    }

    public void save(Contact contact) {
        String contactJson = gson.toJson(contact);
        preferences.edit()
                .putString(contactsPrefix + contact.getId().toString(), contactJson)
                .apply();
    }
}
