package io.teak.sdk;

import android.support.annotation.NonNull;

import io.teak.sdk.event.OSListener;

/**
 * Created by pat on 10/6/17.
 */

public interface ObjectFactory {
    @NonNull OSListener getOSListener();
}
