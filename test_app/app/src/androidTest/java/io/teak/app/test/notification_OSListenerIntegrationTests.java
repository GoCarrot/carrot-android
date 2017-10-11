/* Teak -- Copyright (C) 2017 GoCarrot Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.teak.app.test;


import android.content.Context;
import android.content.Intent;
import android.support.test.filters.SdkSuppress;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.teak.sdk.TeakNotification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 21)
public class notification_OSListenerIntegrationTests extends TeakIntegrationTests {

    // TODO: ADM, if anyone uses it ever

    @Test
    public void notificationReceived() {
        launchActivity();

        // Send a notification received broadcast
        sendBroadcast("com.google.android.c2dm.intent.RECEIVE");

        // Should have been called
        verify(osListener, timeout(7000).times(1)).notification_onNotificationReceived(any(Context.class), any(Intent.class));
    }

    @Test
    public void notificationAction() {
        launchActivity();

        // Send a notification opened broadcast
        String event = getActivity().getPackageName() + TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX;
        sendBroadcast(event);

        // Should have been called
        verify(osListener, timeout(7000).times(1)).notification_onNotificationAction(any(Context.class), any(Intent.class));
    }

    @Test
    public void notificationCleared() {
        launchActivity();

        // Send a notification cleared broadcast
        String event = getActivity().getPackageName() + TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX;
        sendBroadcast(event);

        // Should have been called
        verify(osListener, timeout(7000).times(1)).notification_onNotificationCleared(any(Context.class), any(Intent.class));
    }
}
