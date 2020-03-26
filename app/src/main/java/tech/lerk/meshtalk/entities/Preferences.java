package tech.lerk.meshtalk.entities;

import androidx.annotation.NonNull;

public enum Preferences {
    FIRST_START("first_start"),
    SELF_DESTRUCT("self_destruct"),
    DEVICE_IV("device_iv"),
    IDENTITIES("identities"),
    MESSAGES("messages"),
    CONTACTS("contacts"),
    CHATS("chats"),
    DEFAULT_IDENTITY("default_identity"),
    CURRENT_CHAT("current_chat"),
    ASK_CREATING_CHAT("ask_creating_chat"),
    USE_MESSAGE_GATEWAY("use_message_gateway"),
    MESSAGE_GATEWAY("message_gateway");

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
