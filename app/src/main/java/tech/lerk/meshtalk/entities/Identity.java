package tech.lerk.meshtalk.entities;

import java.security.PrivateKey;
import java.util.List;
import java.util.UUID;

public class Identity extends Contact {
    private PrivateKey privateKey;
    private List<UUID> chats;

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public List<UUID> getChats() {
        return chats;
    }

    public void setChats(List<UUID> chats) {
        this.chats = chats;
    }
}
