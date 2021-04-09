package io.teak.sdk.facebook;

import com.facebook.AccessToken;

import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.FacebookAccessTokenEvent;

public class AccessTokenTracker extends com.facebook.AccessTokenTracker {

    @Override
    protected void onCurrentAccessTokenChanged(AccessToken oldAccessToken, AccessToken currentAccessToken) {
        if (currentAccessToken == null) return;

        final String accessTokenString = currentAccessToken.getToken();
        if (accessTokenString != null) {
            TeakEvent.postEvent(new FacebookAccessTokenEvent(accessTokenString));
        }
    }
}
