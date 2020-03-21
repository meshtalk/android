package tech.lerk.meshtalk.entities;

import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

public class Contact {
    private UUID id;
    private String name;
    private RSAPublicKey publicKey;
    private List<UUID> chats;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(RSAPublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public List<UUID> getChats() {
        return chats;
    }

    public void setChats(List<UUID> chats) {
        this.chats = chats;
    }
}
