package tech.lerk.meshtalk.entities.ui;

import com.stfalcon.chatkit.commons.models.IMessage;

import java.util.Date;
import java.util.UUID;

public class MessageDO implements IMessage, Comparable<MessageDO> {
    private final String id;
    private final String text;
    private final UserDO user;
    private final Date date;

    public MessageDO(String id, String text, UserDO user, Date date) {
        this.id = id;
        this.text = text;
        this.user = user;
        this.date = date;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public UserDO getUser() {
        return user;
    }

    @Override
    public Date getCreatedAt() {
        return date;
    }

    @Override
    public int compareTo(MessageDO o) {
        return UUID.fromString(getId())
                .compareTo(UUID.fromString(o.getId()));
    }
}
