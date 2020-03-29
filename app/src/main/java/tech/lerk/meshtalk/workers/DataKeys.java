package tech.lerk.meshtalk.workers;

import androidx.annotation.NonNull;

public enum DataKeys {
    // META
    API_VERSION("api_version"),
    CORE_VERSION("core_version"),
    ERROR_CODE("error_code"),
    // MESSAGE
    MESSAGE_ID("id"),
    MESSAGE_SENDER("sender"),
    MESSAGE_RECEIVER("receiver"),
    MESSAGE_CHAT("chat"),
    MESSAGE_DATE("date"),
    MESSAGE_CONTENT("content"),
    // HANDSHAKE
    HANDSHAKE_ID("id"),
    HANDSHAKE_SENDER("sender"),
    HANDSHAKE_RECEIVER("receiver"),
    HANDSHAKE_CHAT("chat"),
    HANDSHAKE_DATE("date"),
    HANDSHAKE_CONTENT("content"),
    HANDSHAKE_KEY("key"),
    // MESSAGE LIST
    MESSAGE_LIST_SIZE("message_list_size"),
    MESSAGE_LIST_ELEMENT_PREFIX("message_list_elem_");

    private final String key;

    DataKeys(String key) {
        this.key = key;
    }

    @NonNull
    @Override
    public String toString() {
        return key;
    }
}
