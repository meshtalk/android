package tech.lerk.meshtalk.providers.impl;

import android.content.Context;
import android.os.AsyncTask;
import android.telecom.Call;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.KeyHolder;
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.db.entities.IdentityDbo;
import tech.lerk.meshtalk.entities.Identity;
import tech.lerk.meshtalk.exceptions.DecryptionException;
import tech.lerk.meshtalk.exceptions.EncryptionException;
import tech.lerk.meshtalk.providers.DatabaseProvider;

public class IdentityProvider extends DatabaseProvider<Identity> {
    private static final String TAG = IdentityProvider.class.getCanonicalName();
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
    public void getById(UUID id, @NonNull Callback<Identity> callback) {
        AsyncTask.execute(() -> {
            IdentityDbo identityById = database.identityDao().getIdentityById(id);
            if (identityById == null) {
                callback.call(null);
            } else {
                try {
                    callback.call(DatabaseEntityConverter.convert(identityById, keyHolder));
                } catch (DecryptionException e) {
                    Log.e(TAG, "Unable to decrypt identity!", e);
                    callback.call(null);
                }
            }
        });
    }

    @Override
    public void save(Identity identity) {
        AsyncTask.execute(() -> {
            try {
                database.identityDao().insertIdentity(DatabaseEntityConverter.convert(identity, keyHolder));
            } catch (EncryptionException e) {
                Log.e(TAG, "Unable to save identity!", e);
            }
        });
    }

    @Override
    public void getAll(@NonNull Callback<Set<Identity>> callback) {
        AsyncTask.execute(() -> callback.call(database.identityDao().getIdentities().stream()
                .map(idbo -> {
                    try {
                        return DatabaseEntityConverter.convert(idbo, keyHolder);
                    } catch (DecryptionException e) {
                        Log.e(TAG, "Unable to decrypt identity!", e);
                        return null;
                    }
                })
                .collect(Collectors.toCollection(TreeSet::new))));
    }

    @Override
    public void deleteById(UUID id) {
        AsyncTask.execute(() -> database.identityDao().deleteIdentityById(id));
    }

    @Override
    public void delete(Identity element) {
        AsyncTask.execute(() -> {
            try {
                database.identityDao().deleteIdentity(DatabaseEntityConverter.convert(element, keyHolder));
            } catch (EncryptionException e) {
                Log.e(TAG, "Unable to encrypt identity!", e);
            }
        });
    }

    @Override
    public void exists(UUID id, @NonNull Callback<Boolean> callback) {
        AsyncTask.execute(() -> callback.call(database.identityDao().getIdentities().stream().anyMatch(i -> i.getId().equals(id))));
    }

    @Override
    public void deleteAll() {
        database.identityDao().deleteAll();
    }

    public void getAllLiveData(Callback<LiveData<List<IdentityDbo>>> callback) {
        AsyncTask.execute(() -> callback.call(database.identityDao().getIdentitiesLiveData()));
    }
}
