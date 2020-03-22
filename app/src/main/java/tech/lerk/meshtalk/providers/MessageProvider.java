package tech.lerk.meshtalk.providers;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.KeyHolder;
import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.Preferences;

public class MessageProvider implements Provider<Message> {
    private static MessageProvider instance = null;
    private final KeyHolder keyHolder;
    private final SharedPreferences preferences;
    private static final String messagePrefix = "MESSAGE_";
    private final Gson gson;

    private MessageProvider(Context context) {
        keyHolder = KeyHolder.get(context);
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        gson = new Gson();
    }

    public static MessageProvider get(Context context) {
        if (instance == null) {
            instance = new MessageProvider(context);
        }
        return instance;
    }

    @Override
    public Message getById(UUID id) {
        return gson.fromJson(preferences.getString(messagePrefix + id, Stuff.EMPTY_OBJECT), Message.class);
    }

    @Override
    public void save(Message element) {
        Set<String> messages = preferences.getStringSet(Preferences.MESSAGES.toString(), new TreeSet<>());
        messages.add(element.getId().toString());
        preferences.edit()
                .putString(messagePrefix + element.getId(), gson.toJson(element))
                .putStringSet(Preferences.MESSAGES.toString(), messages)
                .apply();
    }

    @Override
    public void deleteById(UUID id) {
        Set<String> messages = preferences.getStringSet(Preferences.MESSAGES.toString(), new TreeSet<>());
        messages.remove(id.toString());
        preferences.edit()
                .remove(messagePrefix + id)
                .putStringSet(Preferences.MESSAGES.toString(), messages)
                .apply();
    }

    @Override
    public Set<UUID> getAllIds() {
        return preferences.getStringSet(Preferences.MESSAGES.toString(), new TreeSet<>()).stream()
                .map(UUID::fromString).collect(Collectors.toCollection(TreeSet::new));
    }
}
