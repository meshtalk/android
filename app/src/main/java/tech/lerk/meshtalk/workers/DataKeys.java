package tech.lerk.meshtalk.workers;

import androidx.annotation.NonNull;

public enum DataKeys {
    API_VERSION("api_version"),
    CORE_VERSION("core_version"),
    ERROR_CODE("error_code");


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
