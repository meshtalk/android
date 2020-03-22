package tech.lerk.meshtalk.entities;

import java.util.List;
import java.util.UUID;

public class Chat {
    private UUID id;
    private String title;
    private UUID recipient;

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
}
