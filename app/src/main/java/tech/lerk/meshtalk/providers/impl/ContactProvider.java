package tech.lerk.meshtalk.providers.impl;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.Callback;
import tech.lerk.meshtalk.Utils;
import tech.lerk.meshtalk.db.DatabaseEntityConverter;
import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.providers.DatabaseProvider;

public class ContactProvider extends DatabaseProvider<Contact> {
    private static ContactProvider instance = null;

    private ContactProvider(Context context) {
        super(context);
    }

    public static ContactProvider get(Context context) {
        if (instance == null) {
            instance = new ContactProvider(context);
        }
        return instance;
    }

    public String getAsShareableJSON(Contact contact) {
        Contact c = new Contact();
        c.setName(contact.getName());
        c.setPublicKey(contact.getPublicKey());
        c.setId(contact.getId());
        return Utils.getGson().toJson(c);
    }

    @Override
    public void getById(UUID id, @NonNull Callback<Contact> callback) {
        AsyncTask.execute(() -> callback.call(DatabaseEntityConverter.convert(database.contactDao().getContactById(id))));
    }

    @Override
    public void save(Contact contact) {
        AsyncTask.execute(() -> database.contactDao().insertContact(DatabaseEntityConverter.convert(contact)));
    }

    @Override
    public void deleteById(UUID id) {
        AsyncTask.execute(() -> database.contactDao().deleteContactById(id));
    }

    @Override
    public void delete(Contact element) {
        AsyncTask.execute(() -> database.contactDao().deleteContact(DatabaseEntityConverter.convert(element)));
    }

    @Override
    public void exists(UUID id, @NonNull Callback<Boolean> callback) {
        AsyncTask.execute(() -> callback.call(database.contactDao().getContacts().stream().anyMatch(c -> c.getId().equals(id))));
    }

    @Override
    public void getAll(@NonNull Callback<Set<Contact>> callback) {
        AsyncTask.execute(() -> callback.call(database.contactDao().getContacts().stream()
                .map(DatabaseEntityConverter::convert)
                .collect(Collectors.toCollection(TreeSet::new))));
    }

    @Override
    public void deleteAll() {
        AsyncTask.execute(() -> database.contactDao().deleteAll());
    }
}
