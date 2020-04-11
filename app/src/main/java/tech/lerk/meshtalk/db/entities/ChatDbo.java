package tech.lerk.meshtalk.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

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

    @ColumnInfo(name = "handshake")
    private UUID handshake;

    public ChatDbo(@NonNull UUID id, String title, UUID recipient, UUID sender, UUID handshake) {
        this.id = id;
        this.title = title;
        this.recipient = recipient;
        this.sender = sender;
        this.handshake = handshake;
    }

    @NonNull
    public UUID getId() {
        return id;
    }

    public void setId(@NonNull UUID id) {
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

    public UUID getHandshake() {
        return handshake;
    }

    public void setHandshake(UUID handshake) {
        this.handshake = handshake;
    }
}
