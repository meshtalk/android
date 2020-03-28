package tech.lerk.meshtalk.providers;

import java.util.Set;
import java.util.UUID;

import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.exceptions.EncryptionException;

public interface Provider<T> {
    T getById(UUID id) throws DecryptionException;
    void save(T element) throws EncryptionException;
    void deleteById(UUID id);
    boolean exists(UUID id);
    Set<UUID> getAllIds();
}
