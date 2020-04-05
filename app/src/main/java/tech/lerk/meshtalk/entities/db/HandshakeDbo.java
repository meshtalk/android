package tech.lerk.meshtalk.entities.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity(tableName = "handshake")
public class HandshakeDbo {
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "id")
    private UUID id;

    @ColumnInfo(name = "sender")
    private UUID sender;

    @ColumnInfo(name = "receiver")
    private UUID receiver;

    @ColumnInfo(name = "date")
    private LocalDateTime date;

    @ColumnInfo(name = "chat")
    private UUID chat;

    @ColumnInfo(name = "key")
    private String key;

    public HandshakeDbo(UUID id, UUID sender, UUID receiver, LocalDateTime date, UUID chat, String key) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.date = date;
        this.chat = chat;
        this.key = key;
    }

    @Ignore
    public HandshakeDbo() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSender() {
        return sender;
    }

    public void setSender(UUID sender) {
        this.sender = sender;
    }

    public UUID getReceiver() {
        return receiver;
    }

    public void setReceiver(UUID receiver) {
        this.receiver = receiver;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public UUID getChat() {
        return chat;
    }

    public void setChat(UUID chat) {
        this.chat = chat;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
