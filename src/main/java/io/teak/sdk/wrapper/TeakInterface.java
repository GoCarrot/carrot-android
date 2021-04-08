package io.teak.sdk.wrapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import io.teak.sdk.Teak;
import io.teak.sdk.Unobfuscable;
import io.teak.sdk.json.JSONObject;
import java.util.HashMap;

public class TeakInterface implements Unobfuscable {
    private final ISDKWrapper sdkWrapper;

    public TeakInterface(ISDKWrapper sdkWrapper) {
        this.sdkWrapper = sdkWrapper;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Teak.REWARD_CLAIM_ATTEMPT);
        filter.addAction(Teak.LAUNCHED_FROM_NOTIFICATION_INTENT);
        filter.addAction(Teak.FOREGROUND_NOTIFICATION_INTENT);
        filter.addAction(Teak.ADDITIONAL_DATA_INTENT);
        filter.addAction(Teak.LAUNCHED_FROM_LINK_INTENT);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final Bundle bundle = intent.getExtras();
            if (bundle == null) return;

            if (Teak.LAUNCHED_FROM_NOTIFICATION_INTENT.equals(action)) {
                String eventData = "{}";
                try {
                    @SuppressWarnings("unchecked")
                    HashMap<String, Object> eventDataDict = (HashMap<String, Object>) bundle.getSerializable("eventData");
                    eventData = new JSONObject(eventDataDict).toString();
                } catch (Exception e) {
                    Teak.log.exception(e);
                } finally {
                    sdkWrapper.sdkSendMessage(ISDKWrapper.EventType.NotificationLaunch, eventData);
                }
            } else if (Teak.REWARD_CLAIM_ATTEMPT.equals(action)) {
                try {
                    @SuppressWarnings("unchecked")
                    HashMap<String, Object> reward = (HashMap<String, Object>) bundle.getSerializable("reward");

                    String eventData = new JSONObject(reward).toString();
                    sdkWrapper.sdkSendMessage(ISDKWrapper.EventType.RewardClaim, eventData);
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            } else if (Teak.FOREGROUND_NOTIFICATION_INTENT.equals(action)) {
                String eventData = "{}";
                try {
                    @SuppressWarnings("unchecked")
                    HashMap<String, Object> eventDataDict = (HashMap<String, Object>) bundle.getSerializable("eventData");
                    eventData = new JSONObject(eventDataDict).toString();
                } catch (Exception e) {
                    Teak.log.exception(e);
                } finally {
                    sdkWrapper.sdkSendMessage(ISDKWrapper.EventType.ForegroundNotification, eventData);
                }
            } else if (Teak.ADDITIONAL_DATA_INTENT.equals(action)) {
                String eventData = "{}";
                try {
                    eventData = bundle.getString("additional_data");
                } catch (Exception e) {
                    Teak.log.exception(e);
                } finally {
                    sdkWrapper.sdkSendMessage(ISDKWrapper.EventType.AdditionalData, eventData);
                }
            } else if (Teak.LAUNCHED_FROM_LINK_INTENT.equals(action)) {
                String eventData = "{}";
                try {
                    eventData = bundle.getString("linkInfo");
                } catch (Exception e) {
                    Teak.log.exception(e);
                } finally {
                    sdkWrapper.sdkSendMessage(ISDKWrapper.EventType.LaunchedFromLink, eventData);
                }
            }
        }
    };
}
