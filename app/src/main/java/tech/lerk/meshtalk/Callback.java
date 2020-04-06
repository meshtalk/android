package tech.lerk.meshtalk;

import androidx.annotation.Nullable;

public interface Callback<V> {
    void call(@Nullable V value);

    interface Multi<A, B> {
        void call(@Nullable A a, @Nullable B b);
    }
}
