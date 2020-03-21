package tech.lerk.meshtalk.entities;

import java.security.interfaces.RSAPrivateKey;

public class Identity extends Contact {
    private RSAPrivateKey privateKey;

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(RSAPrivateKey privateKey) {
        this.privateKey = privateKey;
    }
}
