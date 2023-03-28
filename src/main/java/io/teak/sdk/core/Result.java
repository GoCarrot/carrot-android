package io.teak.sdk.core;

import androidx.annotation.NonNull;

public class Result<T> {
    public final T value;
    public final Exception error;

    public Result(final T value, final Exception error) {
        this.value = value;
        this.error = error;
    }

    public Result(final T value) {
        this(value, null);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof Result<?>) ) return false;
        if (obj == this) return true;

        final Result<?> result = (Result<?>) obj;
        return result.value == this.value && result.error == this.error;
    }

    @Override
    public int hashCode() {
        int hashCode = error != null ? error.hashCode() : 0;
        hashCode = 31 * hashCode + (value != null ? value.hashCode() : 0);
        return hashCode;
    }

    @NonNull
    @Override
    public String toString() {
        return getClass().getName() + '@' + Integer.toHexString(hashCode()) +
            "[value=" + (value == null ? "null" : value.toString()) +
            ", error=" + (error == null ? "null" : error.toString()) + "]";
    }
}
