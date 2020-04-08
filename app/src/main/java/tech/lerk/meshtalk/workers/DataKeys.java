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
    HANDSHAKE_REPLY("reply"),
    HANDSHAKE_CHAT("chat"),
    HANDSHAKE_DATE("date"),
    HANDSHAKE_KEY("key"),
    HANDSHAKE_IV("iv"),
    // HANDSHAKE LIST
    HANDSHAKE_LIST_SIZE("handshake_list_size"),
    HANDSHAKE_LIST_ELEMENT_PREFIX("handshake_list_elem_");

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
