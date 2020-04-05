package tech.lerk.meshtalk.providers.impl;

import android.content.Context;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.db.ChatDbo;
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
    public void getById(UUID id, LookupCallback<Chat> callback) {
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
    public void exists(UUID id, LookupCallback<Boolean> callback) {
        callback.call(database.chatDao().getChats().stream().anyMatch(c -> c.getId().equals(id)));
    }

    @Override
    public void getAllIds(LookupCallback<Set<UUID>> callback) {
        callback.call(database.chatDao().getChats().stream()
                .map(ChatDbo::getId).collect(Collectors.toCollection(TreeSet::new)));
    }
}
