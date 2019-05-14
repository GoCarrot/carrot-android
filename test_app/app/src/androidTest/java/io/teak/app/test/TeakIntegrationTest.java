package io.teak.app.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.rule.ActivityTestRule;
import android.support.test.uiautomator.UiDevice;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;

import java.io.BufferedInputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Map;

import io.teak.sdk.DefaultObjectFactory;
import io.teak.sdk.IObjectFactory;
import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.core.ITeakCore;
import io.teak.sdk.core.InstrumentableReentrantLock;
import io.teak.sdk.core.TeakCore;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.io.DefaultAndroidDeviceInfo;
import io.teak.sdk.io.DefaultAndroidNotification;
import io.teak.sdk.io.IAndroidDeviceInfo;
import io.teak.sdk.io.IAndroidNotification;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.push.IPushProvider;
import io.teak.sdk.store.IStore;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"WeakerAccess", "unused"})
class TeakIntegrationTest {
    IStore store;
    IAndroidResources androidResources;
    TestTeakEventListener eventListener;
    ITeakCore teakCore;
    private final boolean useDefaultTeakCore;

    private final DefaultAndroidDeviceInfo androidDeviceInfo;
    private final IPushProvider pushProvider;
    private final IAndroidNotification androidNotification;

    TeakIntegrationTest() {
        this(false);
    }

    TeakIntegrationTest(boolean useDefaultTeakCore) {
        try {
            final Context context = InstrumentationRegistry.getTargetContext();
            this.androidDeviceInfo = new DefaultAndroidDeviceInfo(context);
            this.pushProvider = DefaultObjectFactory.createPushProvider(context);
            this.androidNotification = new DefaultAndroidNotification(context);

            this.useDefaultTeakCore = useDefaultTeakCore;
            if (this.useDefaultTeakCore) {
                this.teakCore = new TeakCore(context);
            }
        } catch (IntegrationChecker.MissingDependencyException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Before
    public void resetTeakEventListeners() throws NoSuchFieldException, IllegalAccessException {
        // Enable lock contention timeout/checks
        InstrumentableReentrantLock.interruptLongLocksAndReport = true;
        if (!this.useDefaultTeakCore) {
            TestHelpers.resetTeakEventListeners();
        }
    }

    @AfterClass
    public static void stopEventProcessingThread() {
        TeakEvent.postEvent(TeakEvent.StopEvent);
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

            // Teak Core mock
            if (!useDefaultTeakCore) {
                teakCore = mock(ITeakCore.class);
            }

            // Android Resources mock
            androidResources = mock(io.teak.sdk.io.IAndroidResources.class);
            when(androidResources.getStringResource(AppConfiguration.TEAK_APP_ID_RESOURCE)).thenReturn("1919749661621253");
            when(androidResources.getStringResource(AppConfiguration.TEAK_API_KEY_RESOURCE)).thenReturn("2cd84c8899833f08c48aca2e1909b6c5");

            // Create and add an easily mockable TeakEvent.EventListener
            if (eventListener != null) {
                TeakEvent.removeEventListener(eventListener);
            }
            eventListener = spy(TestTeakEventListener.class);
            TeakEvent.addEventListener(eventListener);

            MainActivity.whateverFactory = new IObjectFactory() {
                @Nullable
                @Override
                public IStore getIStore() {
                    return store;
                }

                @NonNull
                @Override
                public IAndroidResources getAndroidResources() {
                    return androidResources;
                }

                @NonNull
                @Override
                public IAndroidDeviceInfo getAndroidDeviceInfo() {
                    return androidDeviceInfo;
                }

                @Nullable
                @Override
                public IPushProvider getPushProvider() {
                    return pushProvider;
                }

                @NonNull
                @Override
                public IAndroidNotification getAndroidNotification() {
                    return androidNotification;
                }

                @NonNull
                @Override
                public ITeakCore getTeakCore() {
                    return teakCore;
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

    MainActivity getActivity() {
        return testRule.getActivity();
    }

    MainActivity launchActivity() {
        return testRule.launchActivity(null);
    }

    MainActivity launchActivity(@Nullable Intent intent) {
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
        assertNotNull(i);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getTargetContext().startActivity(i);
    }

    ///// BroadcastReceiver test helpers

    @SdkSuppress(minSdkVersion = 21)
    String adbShell(@NonNull String adbString) {
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

    @SdkSuppress(minSdkVersion = 21)
    String sendBroadcast(@NonNull String event) {
        return sendBroadcast(event, null);
    }

    @SdkSuppress(minSdkVersion = 21)
    String sendBroadcast(@NonNull String event, @Nullable Map<String, Object> extras) {
        StringBuilder adbString = new StringBuilder("am broadcast -a " + event);
        if (extras != null) {
            for (Map.Entry<String, Object> entry : extras.entrySet()) {
                if (entry.getValue().getClass() == String.class) {
                    adbString.append(" --es \"").append(entry.getKey()).append("\" \"").append(entry.getValue()).append("\"");
                } else if (entry.getValue().getClass() == boolean.class) {
                    adbString.append(" --ez \"").append(entry.getKey()).append("\" ").append(((boolean) entry.getValue()) ? "true" : "false");
                } else if (entry.getValue().getClass() == int.class) {
                    adbString.append(" --ei \"").append(entry.getKey()).append("\" ").append(entry.getValue());
                } else if (entry.getValue().getClass() == long.class) {
                    adbString.append(" --el \"").append(entry.getKey()).append("\" ").append(entry.getValue());
                } else if (entry.getValue().getClass() == float.class) {
                    adbString.append(" --ef \"").append(entry.getKey()).append("\" ").append(entry.getValue());
                }
            }
        }
        return adbShell(adbString.toString());
    }

    static String dumpWindowHierarchyToString() {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final UiDevice device = UiDevice.getInstance(instrumentation);

        OutputStream output = new OutputStream()
        {
            private StringBuilder string = new StringBuilder();
            @Override
            public void write(int b) throws IOException {
                this.string.append((char) b );
            }

            public String toString() {
                return this.string.toString();
            }
        };
        try {
            device.dumpWindowHierarchy(output);
        } catch (Exception ignored) {
        }
        return output.toString();
    }
}
