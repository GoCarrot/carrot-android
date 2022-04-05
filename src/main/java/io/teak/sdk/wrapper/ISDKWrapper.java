package io.teak.sdk.wrapper;

import androidx.annotation.NonNull;

public interface ISDKWrapper {
    enum EventType {
        NotificationLaunch,
        RewardClaim,
        ForegroundNotification,
        AdditionalData,
        LaunchedFromLink,
        PostLaunchSummary,
        UserData
    }
    void sdkSendMessage(@NonNull EventType eventType, @NonNull String eventData);
}
