package io.teak.sdk.wrapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import io.teak.sdk.Unobfuscable;
import io.teak.sdk.json.JSONObject;

import java.util.HashMap;
import java.util.concurrent.FutureTask;

import io.teak.sdk.Teak;

public class TeakInterface implements Unobfuscable {
    private final ISDKWrapper sdkWrapper;
    private final FutureTask<Void> deepLinksReadyTask;

    public TeakInterface(ISDKWrapper sdkWrapper) {
        this.sdkWrapper = sdkWrapper;
        this.deepLinksReadyTask = new FutureTask<>(new Runnable() {
            @Override
            public void run() {
                // None
            }
        }, null);
        Teak.waitForDeepLink = deepLinksReadyTask;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Teak.REWARD_CLAIM_ATTEMPT);
        filter.addAction(Teak.LAUNCHED_FROM_NOTIFICATION_INTENT);

        if (Teak.Instance != null) {
            Teak.Instance.objectFactory.getTeakCore().registerLocalBroadcastReceiver(broadcastReceiver, filter);
        }
    }

    public void readyForDeepLinks() {
        this.deepLinksReadyTask.run();
    }

    @SuppressWarnings("FieldCanBeLocal")
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle == null) return;

            String action = intent.getAction();
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
            }
        }
    };
}
