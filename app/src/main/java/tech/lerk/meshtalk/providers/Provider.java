package tech.lerk.meshtalk.providers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;
import java.util.UUID;

import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.exceptions.EncryptionException;

public interface Provider<T> {
    @Nullable
    T getById(UUID id) throws DecryptionException;

    void save(T element) throws EncryptionException;

    void deleteById(UUID id);

    boolean exists(UUID id);

    @NonNull
    Set<UUID> getAllIds();
}
