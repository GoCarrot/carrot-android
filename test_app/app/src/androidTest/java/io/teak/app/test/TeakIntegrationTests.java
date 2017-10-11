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

import android.app.Activity;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.rule.ActivityTestRule;

import org.junit.Rule;


import java.io.BufferedInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;

import io.teak.sdk.ObjectFactory;
import io.teak.sdk.Teak2;
import io.teak.sdk.event.OSListener;

import static junit.framework.TestCase.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeakIntegrationTests {
    OSListener osListener;

    @Rule
    public ActivityTestRule<MainActivity> testRule = new ActivityTestRule<MainActivity>(MainActivity.class, false, false) {
        @Override
        protected void beforeActivityLaunched() {
            super.beforeActivityLaunched();

            Teak2.Instance = null;

            osListener = mock(io.teak.sdk.event.OSListener.class);
            when(osListener.lifecycle_onActivityCreated(any(Activity.class))).thenReturn(true);

            MainActivity.whateverFactory = new ObjectFactory() {
                @NonNull
                @Override
                public io.teak.sdk.event.OSListener getOSListener() {
                    return osListener;
                }
            };
        }

        @Override
        protected void afterActivityLaunched() {
            super.afterActivityLaunched();
            verify(osListener, times(1)).lifecycle_onActivityCreated(getActivity());
            verify(osListener, times(1)).lifecycle_onActivityResumed(getActivity());
        }

        @Override
        protected void afterActivityFinished() {
            super.afterActivityFinished();
            verify(osListener, timeout(500).atLeastOnce()).lifecycle_onActivityPaused(getActivity());
        }
    };

    ///// Invoke helper for private Teak methods

    private Class<?> teakThunkClass = Teak2.class;

    void call_prime31PurchaseSucceeded(String json) {
        try {
            Method method = teakThunkClass.getDeclaredMethod("prime31PurchaseSucceeded", String.class);
            method.setAccessible(true);
            method.invoke(null, json);
        } catch (Exception e) {
            fail(android.util.Log.getStackTraceString(e));
        }
    }

    void call_openIABPurchaseSucceeded(String json) {
        try {
            Method method = teakThunkClass.getDeclaredMethod("openIABPurchaseSucceeded", String.class);
            method.setAccessible(true);
            method.invoke(null, json);
        } catch (Exception e) {
            fail(android.util.Log.getStackTraceString(e));
        }
    }

    void call_pluginPurchaseFailed(int errorCode) {
        try {
            Method method = teakThunkClass.getDeclaredMethod("pluginPurchaseFailed", int.class);
            method.setAccessible(true);
            method.invoke(null, errorCode);
        } catch (Exception e) {
            fail(android.util.Log.getStackTraceString(e));
        }
    }

    ///// TestRule helpers

    Activity getActivity() {
        return testRule.getActivity();
    }

    Activity launchActivity() {
        return testRule.launchActivity(null);
    }

    Activity launchActivity(@Nullable Intent intent) {
        return testRule.launchActivity(intent);
    }

    ///// Sleep helper, since Mockito.await seems to have strange behavior

    void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception ignored) {
        }
    }

    ///// BroadcastReceiver test helpers

    @SdkSuppress(minSdkVersion = 21)
    String sendBroadcast(@NonNull String event) {
        return sendBroadcast(event, null);
    }

    @SdkSuppress(minSdkVersion = 21)
    String sendBroadcast(@NonNull String event, @Nullable Map<String, Object> extras) {
        String adbString = "am broadcast -a " + event;
        if (extras != null) {
            for (Map.Entry<String, Object> entry : extras.entrySet()) {
                if (entry.getValue().getClass() == String.class) {
                    adbString += " --es \"" + entry.getKey() + "\" \"" + entry.getValue() + "\"";
                } else if (entry.getValue().getClass() == boolean.class) {
                    adbString += " --ez \"" + entry.getKey() + "\" " + (((boolean)entry.getValue()) ? "true" : "false");
                } else if (entry.getValue().getClass() == int.class) {
                    adbString += " --ei \"" + entry.getKey() + "\" " + entry.getValue();
                } else if (entry.getValue().getClass() == long.class) {
                    adbString += " --el \"" + entry.getKey() + "\" " + entry.getValue();
                } else if (entry.getValue().getClass() == float.class) {
                    adbString += " --ef \"" + entry.getKey() + "\" " + entry.getValue();
                }
            }
        }

        String output = null;
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(adbString);
        FileDescriptor fd = pfd.getFileDescriptor();
        InputStream is = new BufferedInputStream(new FileInputStream(fd));
        byte[] buf = new byte[1024];
        try {
            is.read(buf, 0, buf.length);
            output = new String(buf);
            android.util.Log.v("Teak:IntegrationTest", output);
        } catch (Exception e) {
            fail(android.util.Log.getStackTraceString(e));
        } finally {
            try {
                is.close();
            } catch (Exception ignored){
            }
        }

        return output;
    }
}
