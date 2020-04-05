package tech.lerk.meshtalk.providers;

import android.content.Context;

import tech.lerk.meshtalk.db.MeshtalkDatabase;

public abstract class DatabaseProvider<T> implements Provider<T> {
    protected final MeshtalkDatabase database;

    protected DatabaseProvider(Context context) {
        database = MeshtalkDatabase.get(context);
    }

    public abstract void deleteAll();
}
