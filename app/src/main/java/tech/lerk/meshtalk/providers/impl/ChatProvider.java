package tech.lerk.meshtalk.providers.impl;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.db.entities.ChatDbo;
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
        AsyncTask.execute(() -> callback.call(DatabaseEntityConverter.convert(database.chatDao().getChatById(id))));
    }

    public void getLiveDataById(UUID id, @NonNull Callback<LiveData<ChatDbo>> callback) {
        AsyncTask.execute(() -> callback.call(database.chatDao().getChatLiveDataById(id)));
    }

    @Override
    public void save(Chat element) {
        AsyncTask.execute(() -> database.chatDao().insertChat(DatabaseEntityConverter.convert(element)));
    }

    @Override
    public void deleteById(UUID id) {
        AsyncTask.execute(() -> database.chatDao().deleteChatById(id));
    }

    @Override
    public void delete(Chat element) {
        AsyncTask.execute(() -> database.chatDao().deleteChat(DatabaseEntityConverter.convert(element)));
    }

    @Override
    public void exists(UUID id, @NonNull Callback<Boolean> callback) {
        AsyncTask.execute(() -> callback.call(database.chatDao().getChats().stream().anyMatch(c -> c.getId().equals(id))));
    }

    @Override
    public void getAll(@NonNull Callback<Set<Chat>> callback) {
        AsyncTask.execute(() -> callback.call(database.chatDao().getChats().stream()
                .map(DatabaseEntityConverter::convert)
                .collect(Collectors.toCollection(TreeSet::new))));
    }

    @Override
    public void deleteAll() {
        AsyncTask.execute(() -> database.chatDao().deleteAll());
    }

    public void getAllLiveData(Callback<LiveData<List<ChatDbo>>> callback) {
        AsyncTask.execute(() -> callback.call(database.chatDao().getChatsLiveData()));
    }
}
