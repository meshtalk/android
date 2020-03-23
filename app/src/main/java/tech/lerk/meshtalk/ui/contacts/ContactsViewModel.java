package tech.lerk.meshtalk.ui.contacts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Set;

import tech.lerk.meshtalk.entities.Contact;

public class ContactsViewModel extends ViewModel {

    private MutableLiveData<Set<Contact>> contacts;

    public ContactsViewModel() {
        contacts = new MutableLiveData<>();
    }

    public LiveData<Set<Contact>> getContacts() {
        return contacts;
    }

    public void setContacts(Set<Contact> contacts) {
        this.contacts.setValue(contacts);
    }
}