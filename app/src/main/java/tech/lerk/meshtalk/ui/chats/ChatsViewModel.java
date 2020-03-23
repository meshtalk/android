package tech.lerk.meshtalk.ui.chats;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Set;

import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Contact;

public class ChatsViewModel extends ViewModel {

    private MutableLiveData<Set<Chat>> chats;

    public ChatsViewModel() {
        chats = new MutableLiveData<>();
    }

    public LiveData<Set<Chat>> getChats() {
        return chats;
    }

    public void setChats(Set<Chat> chats) {
        this.chats.setValue(chats);
    }
}