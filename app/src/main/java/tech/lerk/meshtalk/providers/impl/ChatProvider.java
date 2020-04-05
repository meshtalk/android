package tech.lerk.meshtalk.providers.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.providers.DatabaseProvider;

public class ChatProvider extends DatabaseProvider<Chat> {
    private static ChatProvider instance = null;

    private ChatProvider(Context context) {
        super(context);
    }

    public static ChatProvider get(Context context) {
        if (instance == null) {
            instance = new ChatProvider(context);
        }
        return instance;
    }

    @Override
    public void getById(UUID id, @NonNull Callback<Chat> callback) {
        callback.call(DatabaseEntityConverter.convert(database.chatDao().getChatById(id)));
    }

    @Override
    public void save(Chat element) {
        database.chatDao().insertChat(DatabaseEntityConverter.convert(element));
    }

    @Override
    public void deleteById(UUID id) {
        database.chatDao().deleteChatbyId(id);
    }

    @Override
    public void delete(Chat element) {
        database.chatDao().deleteChat(DatabaseEntityConverter.convert(element));
    }

    @Override
    public void exists(UUID id, @NonNull Callback<Boolean> callback) {
        callback.call(database.chatDao().getChats().stream().anyMatch(c -> c.getId().equals(id)));
    }

    @Override
    public void getAll(@NonNull Callback<Set<Chat>> callback) {
        callback.call(database.chatDao().getChats().stream()
                .map(DatabaseEntityConverter::convert)
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    @Override
    public void deleteAll() {
        database.chatDao().deleteAll();
    }
}
