package tech.lerk.meshtalk.ui.conversation;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.ui.MessageDO;
import tech.lerk.meshtalk.providers.Provider;
import tech.lerk.meshtalk.providers.impl.MessageProvider;

public class ConversationViewModel extends ViewModel {

    private MutableLiveData<Set<MessageDO>> messages;

    public ConversationViewModel() {
        messages = new MutableLiveData<>(new TreeSet<>());
    }

    public MutableLiveData<Set<MessageDO>> getMessages() {
        return messages;
    }

    public void setMessages(Set<UUID> msgs, Chat chat, Context context) {
        msgs.forEach(m -> buildMessage(chat, context, m, mdo -> {
            Set<MessageDO> value = messages.getValue();
            if (value == null) {
                value = new TreeSet<>();
            }
            value.add(mdo);
            messages.setValue(value);
        }));
    }

    private void buildMessage(Chat chat, Context context, UUID m, Provider.LookupCallback<MessageDO> callback) {
        MessageProvider messageProvider = MessageProvider.get(context);
        messageProvider.getById(m, msg ->
                messageProvider.decryptMessage(msg, chat, messageText ->
                        Stuff.getUserDO(msg.getSender(), context, messageUser ->
                                callback.call(new MessageDO(
                                        msg.getId().toString(),
                                        messageText,
                                        messageUser, msg.getDate())))));
    }
}