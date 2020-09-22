package io.teak.sdk.event;

import androidx.annotation.NonNull;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.json.JSONObject;

public class LaunchedFromLinkEvent extends TeakEvent {
    public static final String Type = "LaunchedFromLinkEvent";

    public final JSONObject linkInfo;

    public LaunchedFromLinkEvent(@NonNull JSONObject linkInfo) {
        super(Type);

        this.linkInfo = linkInfo;
    }
}
