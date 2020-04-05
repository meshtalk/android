package tech.lerk.meshtalk.ui.conversation;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Set;
import java.util.TreeSet;

import tech.lerk.meshtalk.Callback;

import tech.lerk.meshtalk.Stuff;
import tech.lerk.meshtalk.entities.Chat;
import tech.lerk.meshtalk.entities.Message;
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

    public void setMessages(Set<Message> msgs, Chat chat, Activity activity) {
        msgs.forEach(m -> buildMessage(chat, activity, m, mdo -> {
            Set<MessageDO> value = messages.getValue();
            if (value == null) {
                value = new TreeSet<>();
            }
            value.add(mdo);
            final Set<MessageDO> finalValue = value;
            activity.runOnUiThread(() -> messages.setValue(finalValue));
        }));
    }

    private void buildMessage(Chat chat, Context context, Message msg, Callback<MessageDO> callback) {
        MessageProvider messageProvider = MessageProvider.get(context);
        AsyncTask.execute(() ->
                messageProvider.decryptMessage(msg.getContent(), chat, messageText ->
                        Stuff.getUserDO(msg.getSender(), context, messageUser ->
                                callback.call(new MessageDO(
                                        msg.getId().toString(),
                                        messageText,
                                        messageUser, msg.getDate())))));
    }
}