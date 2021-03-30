package io.teak.app.java.dev;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FirebaseMessagingServicePassthru extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // If this is a Teak notification, let Teak process it, otherwise do your processing
        if (io.teak.sdk.push.FCMPushProvider.isTeakNotification(remoteMessage)) {
            io.teak.sdk.push.FCMPushProvider.onMessageReceivedExternal(remoteMessage, getApplicationContext());
        } else {
            // Do your processing here
        }
    }
}
