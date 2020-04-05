package tech.lerk.meshtalk.providers.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.KeyHolder;
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.entities.db.IdentityDbo;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.exceptions.EncryptionException;
import tech.lerk.meshtalk.providers.DatabaseProvider;

public class IdentityProvider extends DatabaseProvider<Identity> {
    private static IdentityProvider instance = null;
    private final KeyHolder keyHolder;

    private IdentityProvider(Context context) {
        super(context);
        keyHolder = KeyHolder.get(context);
    }

    public static IdentityProvider get(Context context) {
        if (instance == null) {
            instance = new IdentityProvider(context);
        }
        return instance;
    }

    @Override
    public void getById(UUID id, @NonNull LookupCallback<Identity> callback) throws DecryptionException {
        IdentityDbo identityById = database.identityDao().getIdentityById(id);
        if(identityById == null) {
            callback.call(null);
        } else {
            callback.call(DatabaseEntityConverter.convert(identityById, keyHolder));
        }
    }

    @Override
    public void save(Identity identity) throws EncryptionException {
        database.identityDao().insertIdentity(DatabaseEntityConverter.convert(identity, keyHolder));
    }

    @Override
    public void getAllIds(LookupCallback<Set<UUID>> callback) {
        callback.call(database.identityDao().getIdentities().stream()
                .map(IdentityDbo::getId).collect(Collectors.toCollection(TreeSet::new)));
    }

    @Override
    public void deleteById(UUID id) {
        database.identityDao().deleteIdentityById(id);
    }

    @Override
    public void exists(UUID id, LookupCallback<Boolean> callback) {
        callback.call(database.identityDao().getIdentities().stream().anyMatch(i -> i.getId().equals(id)));
    }
}
