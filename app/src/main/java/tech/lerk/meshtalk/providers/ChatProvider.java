package tech.lerk.meshtalk.providers;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.Preferences;

public class ChatProvider implements Provider<Chat> {
    private static ChatProvider instance = null;
    private final SharedPreferences preferences;
    private static final String chatsPrefix = "CHAT_";
    private final Gson gson;

    private ChatProvider(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        gson = new GsonBuilder()
                .registerTypeAdapter(PrivateKey.class, new PrivateKeyTypeAdapter())
                .registerTypeAdapter(PublicKey.class, new PublicKeyTypeAdapter())
                .registerTypeAdapter(Message.class, RuntimeTypeAdapterFactory.of(Message.class, "type")
                        .registerSubtype(Message.class, Message.class.getName())
                        .registerSubtype(Chat.Handshake.class, Chat.Handshake.class.getName()))
                .create();
    }

    public static ChatProvider get(Context context) {
        if (instance == null) {
            instance = new ChatProvider(context);
        }
        return instance;
    }

    @Override
    public Chat getById(UUID id) {
        String chatJson = preferences.getString(chatsPrefix + id.toString(), Stuff.EMPTY_OBJECT);
        return gson.fromJson(chatJson, Chat.class);
    }

    @Override
    public void save(Chat element) {
        Set<String> chats = preferences.getStringSet(Preferences.CHATS.toString(), new TreeSet<>());
        chats.add(element.getId().toString());
        String chatJson = gson.toJson(element);
        preferences.edit()
                .putString(chatsPrefix + element.getId().toString(), chatJson)
                .putStringSet(Preferences.CHATS.toString(), chats)
                .apply();
    }

    @Override
    public void deleteById(UUID id) {
        Set<String> identities = preferences.getStringSet(Preferences.CHATS.toString(), new TreeSet<>());
        identities.remove(id.toString());
        preferences.edit()
                .remove(chatsPrefix + id)
                .putStringSet(Preferences.CHATS.toString(), identities)
                .apply();
    }

    @Override
    public boolean exists(UUID id) {
        return preferences.getString(chatsPrefix + id.toString(), null) != null;
    }

    @Override
    public Set<UUID> getAllIds() {
        return preferences.getStringSet(Preferences.CHATS.toString(), new TreeSet<>())
                .stream().map(UUID::fromString).collect(Collectors.toCollection(TreeSet::new));
    }
}
