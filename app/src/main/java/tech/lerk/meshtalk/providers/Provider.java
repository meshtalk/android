package tech.lerk.meshtalk.providers;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.UUID;

import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.exceptions.EncryptionException;

public interface Provider<T> {

    void getById(UUID id, @NonNull LookupCallback<T> callback) throws DecryptionException;

    void save(T element) throws EncryptionException;

    void deleteById(UUID id);

    void delete(T element);

    void exists(UUID id, @NonNull LookupCallback<Boolean> callback);

    void getAll(@NonNull LookupCallback<Set<T>> callback);

    public static interface LookupCallback<V> {
        void call(V value);
    }
}
