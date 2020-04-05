package tech.lerk.meshtalk.entities.ui;

import com.stfalcon.chatkit.commons.models.IMessage;

import java.sql.Time;
import java.time.LocalDateTime;
import java.time.temporal.TemporalField;
import java.util.Date;
import java.util.GregorianCalendar;

public class MessageDO implements IMessage, Comparable<MessageDO> {
    private final String id;
    private final String text;
    private final UserDO user;
    private final LocalDateTime date;

    public MessageDO(String id, String text, UserDO user, LocalDateTime date) {
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
        return new GregorianCalendar(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                date.getHour(),
                date.getMinute(),
                date.getSecond()).getTime();
    }

    @Override
    public int compareTo(MessageDO o) {
        return getCreatedAt().compareTo(o.getCreatedAt());
    }
}
