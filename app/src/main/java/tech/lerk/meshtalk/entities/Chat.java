package tech.lerk.meshtalk.entities;

import java.util.Set;
import java.util.UUID;

public class Chat implements Comparable<Chat> {
    private UUID id;
    private String title;
    private UUID recipient;
    private UUID sender;
    private Set<UUID> messages;
    private Handshake handshake;

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

    public Set<UUID> getMessages() {
        return messages;
    }

    public void setMessages(Set<UUID> messages) {
        this.messages = messages;
    }

    @Override
    public int compareTo(Chat o) {
        return getId().compareTo(o.getId());
    }

    public UUID getSender() {
        return sender;
    }

    public void setSender(UUID sender) {
        this.sender = sender;
    }

    public Handshake getHandshake() {
        return handshake;
    }

    public void setHandshake(Handshake handshake) {
        this.handshake = handshake;
    }

    public static class Handshake extends Message {
        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }
}
