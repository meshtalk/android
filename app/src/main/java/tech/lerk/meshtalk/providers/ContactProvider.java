package tech.lerk.meshtalk.providers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.adapters.PrivateKeyTypeAdapter;
import tech.lerk.meshtalk.adapters.PublicKeyTypeAdapter;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.Preferences;

public class ContactProvider implements Provider<Contact> {
    private static final String TAG = ContactProvider.class.getCanonicalName();
    private static ContactProvider instance = null;
    private final SharedPreferences preferences;
    private static final String contactsPrefix = "CONTACT_";
    private final Gson gson;

    private ContactProvider(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        gson = new GsonBuilder()
                .registerTypeAdapter(PrivateKey.class, new PrivateKeyTypeAdapter())
                .registerTypeAdapter(PublicKey.class, new PublicKeyTypeAdapter())
                .registerTypeAdapter(Message.class, RuntimeTypeAdapterFactory.of(Message.class, "type")
                        .registerSubtype(Message.class, Message.class.getName())
                        .registerSubtype(Chat.Handshake.class, Chat.Handshake.class.getName()))
                .create();
    }

    public static ContactProvider get(Context context) {
        if (instance == null) {
            instance = new ContactProvider(context);
        }
        return instance;
    }

    public String getAsShareableJSON(UUID id) {
        return getAsShareableJSON(getById(id));
    }

    public String getAsShareableJSON(Contact contact) {
        Contact c = new Contact();
        c.setName(contact.getName());
        c.setPublicKey(contact.getPublicKey());
        c.setId(contact.getId());
        return gson.toJson(c);
    }

    @Override
    public Contact getById(UUID id) {
        String contactJson = preferences.getString(contactsPrefix + id.toString(), Stuff.EMPTY_OBJECT);
        try {
            return gson.fromJson(contactJson, Contact.class);
        } catch (JsonSyntaxException e) {
            Log.w(TAG, "Unable to parse contact!", e);
            return null;
        }
    }

    @Override
    public void save(Contact contact) {
        Set<String> contacts = preferences.getStringSet(Preferences.CONTACTS.toString(), new TreeSet<>());
        contacts.add(contact.getId().toString());
        String contactJson = gson.toJson(contact);
        preferences.edit()
                .putString(contactsPrefix + contact.getId().toString(), contactJson)
                .putStringSet(Preferences.CONTACTS.toString(), contacts)
                .apply();
    }

    @Override
    public void deleteById(UUID id) {
        Set<String> contacts = preferences.getStringSet(Preferences.CONTACTS.toString(), new TreeSet<>());
        contacts.remove(id.toString());
        preferences.edit()
                .remove(contactsPrefix + id)
                .putStringSet(Preferences.CONTACTS.toString(), contacts)
                .apply();
    }

    @Override
    public boolean exists(UUID id) {
        return preferences.getString(contactsPrefix + id.toString(), null) != null;
    }

    @Override
    public Set<UUID> getAllIds() {
        return preferences.getStringSet(Preferences.CONTACTS.toString(), new TreeSet<>()).stream()
                .map(UUID::fromString).collect(Collectors.toCollection(TreeSet::new));
    }
}
