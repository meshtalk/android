package tech.lerk.meshtalk.ui.conversation;

import androidx.annotation.NonNull;

import javax.crypto.SecretKey;

import tech.lerk.meshtalk.entities.Contact;
import tech.lerk.meshtalk.entities.Message;
import tech.lerk.meshtalk.providers.impl.MessageProvider;

public class UIMessage extends Message {
    private static final String TAG = UIMessage.class.getCanonicalName();
    private String decryptedText;
    private String senderName;

    private UIMessage() {
    }

    public static UIMessage of(@NonNull Message m,
                               @NonNull SecretKey chatKey,
                               @NonNull byte[] chatIv,
                               @NonNull Contact sender,
                               @NonNull MessageProvider messageProvider) {
        UIMessage u = new UIMessage();
        u.setId(m.getId());
        u.setReceiver(m.getReceiver());
        u.setSender(m.getSender());
        u.setDate(m.getDate());
        u.setChat(m.getChat());
        u.setContent(m.getContent());
        u.setSenderName(sender.getName());
        u.setDecryptedText(messageProvider.decryptMessageSync(m.getContent(), chatKey, chatIv));
        return u;
    }

    public String getDecryptedText() {
        return decryptedText;
    }

    public void setDecryptedText(String decryptedText) {
        this.decryptedText = decryptedText;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }
}
