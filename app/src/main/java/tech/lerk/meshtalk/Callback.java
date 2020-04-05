package tech.lerk.meshtalk;

import androidx.annotation.Nullable;

public interface Callback<V> {
    void call(@Nullable V value);
}
