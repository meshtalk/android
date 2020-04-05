package tech.lerk.meshtalk.entities.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import tech.lerk.meshtalk.entities.Handshake;

@Entity(tableName = "chat")
public class ChatDbo {
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "id")
    private UUID id;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "recipient")
    private UUID recipient;

    @ColumnInfo(name = "sender")
    private UUID sender;

    @ColumnInfo(name = "messages")
    private Set<UUID> messages;

    @ColumnInfo(name = "handshakes")
    private HashMap<UUID, Handshake> handshakes;

    public ChatDbo(UUID id, String title, UUID recipient, UUID sender, Set<UUID> messages, HashMap<UUID, Handshake> handshakes) {
        this.id = id;
        this.title = title;
        this.recipient = recipient;
        this.sender = sender;
        this.messages = messages;
        this.handshakes = handshakes;
    }

    @Ignore
    public ChatDbo() {
    }


    public UUID getId() {
        return id;
    }


    public void setId(UUID id) {
        this.id = id;
    }


    public String getTitle() {
        return title;
    }


    public void setTitle(String title) {
        this.title = title;
    }


    public UUID getRecipient() {
        return recipient;
    }


    public void setRecipient(UUID recipient) {
        this.recipient = recipient;
    }


    public UUID getSender() {
        return sender;
    }


    public void setSender(UUID sender) {
        this.sender = sender;
    }


    public Set<UUID> getMessages() {
        return messages;
    }


    public void setMessages(Set<UUID> messages) {
        this.messages = messages;
    }


    public HashMap<UUID, Handshake> getHandshakes() {
        return handshakes;
    }


    public void setHandshakes(HashMap<UUID, Handshake> handshakes) {
        this.handshakes = handshakes;
    }
}
