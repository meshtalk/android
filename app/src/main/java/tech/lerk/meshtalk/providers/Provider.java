package tech.lerk.meshtalk.providers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;
import java.util.UUID;

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.exceptions.EncryptionException;

public interface Provider<T> {

    void getById(UUID id, @NonNull Callback<T> callback) throws DecryptionException;

    void save(T element) throws EncryptionException;

    void deleteById(UUID id);

    void delete(T element);

    void exists(UUID id, @NonNull Callback<Boolean> callback);

    void getAll(@NonNull Callback<Set<T>> callback);
}
