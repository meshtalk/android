package tech.lerk.meshtalk.entities;

import androidx.annotation.NonNull;

public enum Preferences {
    FIRST_START("first_start"),
    SELF_DESTRUCT("self_destruct"),
    DEVICE_IV("device_iv"),
    IDENTITIES("identities"),
    DEFAULT_IDENTITY("default_identity");

    private final String key;

    Preferences(String key) {
        this.key = key;
    }

    @NonNull
    @Override
    public String toString() {
        return key;
    }
}
