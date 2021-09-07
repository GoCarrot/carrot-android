package io.teak.sdk.wrapper;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import io.teak.sdk.Teak;
import io.teak.sdk.Unobfuscable;

public class TeakInterface implements Unobfuscable {
    private final ISDKWrapper sdkWrapper;

    public TeakInterface(ISDKWrapper sdkWrapper) {
        this.sdkWrapper = sdkWrapper;

        EventBus.getDefault().register(this);
    }

    @Subscribe
    public void onNotification(Teak.NotificationEvent event) {
        String eventData = "{}";
        try {
            eventData = event.toJSON().toString(0);
        } catch (Exception e) {
            Teak.log.exception(e);
        } finally {
            sdkWrapper.sdkSendMessage(event.isForeground ? ISDKWrapper.EventType.ForegroundNotification : ISDKWrapper.EventType.NotificationLaunch, eventData);
        }
    }

    @Subscribe
    public void onAdditionalData(Teak.AdditionalDataEvent event) {
        String eventData = "{}";
        try {
            // The toString with an indentFactor argument can throw an exception
            // whereas just toString() will return null. We want the exception
            // so that we can send the empty hash.
            eventData = event.additionalData.toString(0);
        } catch (Exception e) {
            Teak.log.exception(e);
        } finally {
            sdkWrapper.sdkSendMessage(ISDKWrapper.EventType.AdditionalData, eventData);
        }
    }

    @Subscribe
    public void onLaunchedFromLink(Teak.LaunchFromLinkEvent event) {
        String eventData = "{}";
        try {
            eventData = event.toJSON().toString(0);
        } catch (Exception e) {
            Teak.log.exception(e);
        } finally {
            sdkWrapper.sdkSendMessage(ISDKWrapper.EventType.LaunchedFromLink, eventData);
        }
    }

    @Subscribe
    public void onRewardClaim(Teak.RewardClaimEvent event) {
        try {
            final String eventData = event.toJSON().toString(0);
            sdkWrapper.sdkSendMessage(ISDKWrapper.EventType.RewardClaim, eventData);
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }
}
