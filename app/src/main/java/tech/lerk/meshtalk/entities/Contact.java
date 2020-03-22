package tech.lerk.meshtalk.entities;

import java.security.PublicKey;
import java.util.UUID;

public class Contact {
    private UUID id;
    private String name;
    private PublicKey publicKey;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }
}
