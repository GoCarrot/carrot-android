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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;

import java.io.BufferedInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import io.teak.sdk.IObjectFactory;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.io.DefaultAndroidDeviceInfo;
import io.teak.sdk.io.IAndroidDeviceInfo;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.store.IStore;

import static junit.framework.TestCase.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeakIntegrationTest {
    IStore store;
    IAndroidResources androidResources;
    TestTeakEventListener eventListener;

    private final DefaultAndroidDeviceInfo androidDeviceInfo;

    TeakIntegrationTest() {
        this.androidDeviceInfo  = new DefaultAndroidDeviceInfo(InstrumentationRegistry.getContext());
    }

    @Before
    public void resetTeakEventListeners() throws NoSuchFieldException, IllegalAccessException {
        TestHelpers.resetTeakEventListeners();
    }

    @Rule
    public ActivityTestRule<MainActivity> testRule = new ActivityTestRule<MainActivity>(MainActivity.class, false, false) {
        @Override
        protected void beforeActivityLaunched() {
            super.beforeActivityLaunched();

            // Reset Teak.Instance
            Teak.Instance = null;

            // Create IStore mock
            store = mock(io.teak.sdk.store.IStore.class);

            // Android Resources mock
            androidResources = mock(io.teak.sdk.io.IAndroidResources.class);
            when(androidResources.getStringResource(AppConfiguration.TEAK_APP_ID)).thenReturn("1136371193060244");
            when(androidResources.getStringResource(AppConfiguration.TEAK_API_KEY)).thenReturn("1f3850f794b9093864a0778009744d03");

            // Create and add an easily mockable TeakEvent.EventListener
            if (eventListener != null) {
                TeakEvent.removeEventListener(eventListener);
            }
            eventListener = spy(TestTeakEventListener.class);
            TeakEvent.addEventListener(eventListener);

            MainActivity.whateverFactory = new IObjectFactory() {
                @Nullable
                @Override
                public IStore getIStore(Context context) {
                    return store;
                }

                @NonNull
                @Override
                public IAndroidResources getAndroidResources(Context context) {
                    return androidResources;
                }

                @NonNull
                @Override
                public IAndroidDeviceInfo getAndroidDeviceInfo(Context context) {
                    return androidDeviceInfo;
                }
            };
        }

        @Override
        protected void afterActivityLaunched() {
            super.afterActivityLaunched();
            verify(eventListener, times(1)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Created);
            verify(eventListener, times(1)).eventRecieved(LifecycleEvent.class, LifecycleEvent.Resumed);
        }

        @Override
        protected void afterActivityFinished() {
            super.afterActivityFinished();
            verify(eventListener, timeout(5000).atLeastOnce()).eventRecieved(LifecycleEvent.class, LifecycleEvent.Paused);
        }
    };

    ///// Invoke helper for private Teak methods

    private Class<?> teakThunkClass = Teak.class;

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

    void call_onActivityResult(int resultCode, Intent data) {
        try {
            Method method = teakThunkClass.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class);
            method.setAccessible(true);
            method.invoke(null, 0 /* unused */, resultCode, data);
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

    ///// Activity background/launch helpers

    void backgroundApp() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        getActivity().startActivity(i);
    }

    void foregroundApp() {
        PackageManager manager = getActivity().getPackageManager();
        Intent i = manager.getLaunchIntentForPackage(getActivity().getPackageName());
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(i);
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
            //noinspection ResultOfMethodCallIgnored
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
