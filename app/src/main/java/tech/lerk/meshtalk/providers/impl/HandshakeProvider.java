package tech.lerk.meshtalk.providers.impl;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.db.entities.HandshakeDbo;
import tech.lerk.meshtalk.entities.Handshake;
import tech.lerk.meshtalk.providers.DatabaseProvider;

public class HandshakeProvider extends DatabaseProvider<Handshake> {
    private static final String TAG = HandshakeProvider.class.getCanonicalName();
    private static HandshakeProvider instance = null;

    public static HandshakeProvider get(@NonNull Context context) {
        if (instance == null) {
            instance = new HandshakeProvider(context);
        }
        return instance;
    }

    private HandshakeProvider(@NonNull Context context) {
        super(context);
    }

    @Override
    public void deleteAll() {
        AsyncTask.execute(() -> database.handshakeDao().deleteAll());
    }

    @Override
    public void getById(@NonNull UUID id, @NonNull Callback<Handshake> callback) {
        AsyncTask.execute(() ->
                callback.call(DatabaseEntityConverter.convert(database.handshakeDao().getHandshakeById(id))));
    }

    public void getLiveDataById(@NonNull UUID id, @NonNull Callback<LiveData<HandshakeDbo>> callback) {
        AsyncTask.execute(() -> callback.call(database.handshakeDao().getHandshakeLiveDataById(id)));
    }

    @Override
    public void save(@NonNull Handshake element) {
        AsyncTask.execute(() ->
                database.handshakeDao().insertHandshake(DatabaseEntityConverter.convert(element)));
    }

    @Override
    public void deleteById(@NonNull UUID id) {
        AsyncTask.execute(() -> database.handshakeDao().deleteHandshakeById(id));
    }

    @Override
    public void delete(@NonNull Handshake element) {
        AsyncTask.execute(() -> database.handshakeDao().deleteHandshake(DatabaseEntityConverter.convert(element)));
    }

    @Override
    public void exists(@NonNull UUID id, @NonNull Callback<Boolean> callback) {
        AsyncTask.execute(() ->
                callback.call(database.handshakeDao().getHandshakeById(id) != null));
    }

    @Override
    public void getAll(@NonNull Callback<Set<Handshake>> callback) {
        AsyncTask.execute(() -> callback.call(database.handshakeDao().getHandshakes().stream()
                .map(DatabaseEntityConverter::convert).collect(Collectors.toSet())));
    }

    public void getLatestByReceiver(@NonNull UUID receiverId, @NonNull Callback<Handshake> callback) {
        AsyncTask.execute(() -> callback.call(DatabaseEntityConverter.convert(database.handshakeDao()
                .getHandshakesByReceiver(receiverId).stream()
                .min((m1, m2) -> m1 != null ? m1.getDate().compareTo(m2.getDate()) : null)
                .orElse(null))));
    }
}
