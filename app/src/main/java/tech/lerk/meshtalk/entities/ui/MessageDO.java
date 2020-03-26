package tech.lerk.meshtalk.entities.ui;

import com.stfalcon.chatkit.commons.models.IMessage;

import java.sql.Time;

public class MessageDO implements IMessage, Comparable<MessageDO> {
    private final String id;
    private final String text;
    private final UserDO user;
    private final Time date;

    public MessageDO(String id, String text, UserDO user, Time date) {
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
    public Time getCreatedAt() {
        return date;
    }

    @Override
    public int compareTo(MessageDO o) {
        return getCreatedAt().compareTo(o.getCreatedAt());
    }
}
