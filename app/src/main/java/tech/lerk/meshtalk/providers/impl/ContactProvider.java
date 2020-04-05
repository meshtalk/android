package tech.lerk.meshtalk.providers.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public void getById(UUID id, @NonNull LookupCallback<Contact> callback) {
        callback.call(DatabaseEntityConverter.convert(database.contactDao().getContactById(id)));
    }

    @Override
    public void save(Contact contact) {
        database.contactDao().insertContact(DatabaseEntityConverter.convert(contact));
    }

    @Override
    public void deleteById(UUID id) {
        database.contactDao().deleteContactById(id);
    }

    @Override
    public void delete(Contact element) {
        database.contactDao().deleteContact(DatabaseEntityConverter.convert(element));
    }

    @Override
    public void exists(UUID id, @NonNull LookupCallback<Boolean> callback) {
        callback.call(database.contactDao().getContacts().stream().anyMatch(c -> c.getId().equals(id)));
    }

    @Override
    public void getAll(@NonNull LookupCallback<Set<Contact>> callback) {
        callback.call(database.contactDao().getContacts().stream()
                .map(DatabaseEntityConverter::convert)
                .collect(Collectors.toCollection(TreeSet::new)));
    }

    @Override
    public void deleteAll() {
        database.contactDao().deleteAll();
    }
}
