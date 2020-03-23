package tech.lerk.meshtalk.ui.conversation;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.entities.ui.MessageDO;
import tech.lerk.meshtalk.providers.MessageProvider;

public class ConversationViewModel extends ViewModel {

    private MutableLiveData<Set<MessageDO>> messages;

    public ConversationViewModel() {
        messages = new MutableLiveData<>(new TreeSet<>());
    }

    public MutableLiveData<Set<MessageDO>> getMessages() {
        return messages;
    }

    public void setMessages(Set<Message> msgs, Chat chat, Context context) {
        messages.setValue(msgs.stream()
                .map(m -> new MessageDO(
                        m.getId().toString(),
                        MessageProvider.get(context).decryptMessage(m, chat),
                        Stuff.getUserDO(m.getSender(), context), m.getDate()))
                .collect(Collectors.toCollection(TreeSet::new)));
    }
}