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

package io.teak.sdk;

import android.app.Activity;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.teak.sdk.core.Session;
import io.teak.sdk.event.DeepLinksReadyEvent;
import io.teak.sdk.event.SessionStateEvent;
import io.teak.sdk.io.IAndroidResources;

/**
 * Teak
 */
public class Teak extends BroadcastReceiver {
    private static final String LOG_TAG = "Teak";

    /**
     * Version of the Teak SDK.
     * @deprecated Use the {@link Teak#Version} member instead.
     */
    public static final String SDKVersion = io.teak.sdk.BuildConfig.VERSION_NAME;

    /**
     * Version of the Teak SDK, and Unity/Air SDK if applicable.
     *
     * You must call {@link Teak#onCreate(Activity)} in order to get Unity/Air SDK version info.
     */
    public static final Map<String, Object> Version;

    private static final Map<String, Object> sdkMap = new HashMap<>();
    static {
        sdkMap.put("android", io.teak.sdk.BuildConfig.VERSION_NAME);
        Version = Collections.unmodifiableMap(sdkMap);
    }

    /**
     * Force debug print on/off.
     */
    public static boolean forceDebug;

    /**
     * Is Teak enabled?
     * @return True if Teak is enabled; False if Teak has not been initialized, or is disabled.
     */
    public static boolean isEnabled() {
        return Instance != null && Instance.isEnabled();
    }

    /**
     * Initialize Teak and tell it to listen to the lifecycle events of {@link Activity}.
     * <p/>
     * <p>Call this function from the {@link Activity#onCreate} function of your <code>Activity</code>
     * <b>before</b> the call to <code>super.onCreate()</code></p>
     *
     * @param activity The main <code>Activity</code> of your app.
     */
    @SuppressWarnings("unused")
    public static void onCreate(@NonNull Activity activity) {
        onCreate(activity, null);
    }

    /**
     * Used for internal testing.
     *
     * @param activity The main <code>Activity</code> of your app.
     * @param objectFactory Teak Object Factory to use, or null for default.
     */
    public static void onCreate(@NonNull Activity activity, @Nullable IObjectFactory objectFactory) {
        // Unless something gave us an object factory, use the default one
        if (objectFactory == null) {
            objectFactory = new DefaultObjectFactory(activity.getApplicationContext());
        }

        // Add version info for Unity/Air
        IAndroidResources androidResources = objectFactory.getAndroidResources();
        String wrapperSDKName = androidResources.getStringResource("io_teak_wrapper_sdk_name");
        String wrapperSDKVersion = androidResources.getStringResource("io_teak_wrapper_sdk_version");
        if (wrapperSDKName != null && wrapperSDKVersion != null) {
            Teak.sdkMap.put(wrapperSDKName, wrapperSDKVersion);
        }

        // Create Instance
        if (Instance == null) {
            Instance = new TeakInstance(activity, objectFactory);
        }
    }

    /**
     * Tell Teak about the result of an {@link Activity} started by your app.
     * <p/>
     * <p>This allows Teak to automatically get the results of In-App Purchase events.</p>
     *
     * @param requestCode The <code>requestCode</code> parameter received from {@link Activity#onActivityResult}
     * @param resultCode  The <code>resultCode</code> parameter received from {@link Activity#onActivityResult}
     * @param data        The <code>data</code> parameter received from {@link Activity#onActivityResult}
     */
    @SuppressWarnings("unused")
    public static void onActivityResult(@SuppressWarnings("unused") int requestCode, int resultCode, Intent data) {
        Teak.log.i("lifecycle", Helpers.mm.h("callback", "onActivityResult"));

        if (data != null) {
            checkActivityResultForPurchase(resultCode, data);
        }
    }

    /**
     * @deprecated call {@link Activity#setIntent(Intent)} inside your {@link Activity#onNewIntent(Intent)}.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public static void onNewIntent(Intent intent) {
        Teak.log.e("deprecation.onNewIntent", "Teak.onNewIntent is deprecated, call Activity.onNewIntent() instead.");
    }

    /**
     * Tell Teak how it should identify the current user.
     * <p/>
     * <p>This should be the same way you identify the user in your backend.</p>
     *
     * @param userIdentifier An identifier which is unique for the current user.
     */
    @SuppressWarnings("unused")
    public static void identifyUser(final String userIdentifier) {
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.identifyUser(userIdentifier);
                }
            });
        }
        // TODO: Else throw exception for integration help?
    }

    /**
     * Track an arbitrary event in Teak.
     *
     * @param actionId         The identifier for the action, e.g. 'complete'.
     * @param objectTypeId     The type of object that is being posted, e.g. 'quest'.
     * @param objectInstanceId The specific instance of the object, e.g. 'gather-quest-1'
     */
    @SuppressWarnings("unused")
    public static void trackEvent(final String actionId, final String objectTypeId, final String objectInstanceId) {
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.trackEvent(actionId, objectTypeId, objectInstanceId);
                }
            });
        }
    }

    /**
     * Interface for running code when a deep link is received
     */
    public static abstract class DeepLink {
        public abstract void call(Map<String, Object> parameters);
    }

    /**
     * Register a deep link route with Teak.
     * @param route A Sinatra-style route, eg /path/:capture
     * @param name The name of the deep link, used in the Teak dashboard
     * @param description The description of the deep link, used in the Teak dashboard
     * @param call The code to invoke when this deep link is received
     */
    @SuppressWarnings("unused")
    public static void registerDeepLink(@NonNull String route, @NonNull String name, @NonNull String description, @NonNull Teak.DeepLink call) {
        io.teak.sdk.core.DeepLink.registerRoute(route, name, description, call);
    }

    /**
     * Intent action used by Teak to notify you that the app was launched from a notification.
     * <p/>
     * You can listen for this using a {@link BroadcastReceiver} and the {@link LocalBroadcastManager}.
     * <pre>
     * {@code
     *     IntentFilter filter = new IntentFilter();
     *     filter.addAction(Teak.LAUNCHED_FROM_NOTIFICATION_INTENT);
     *     LocalBroadcastManager.getInstance(context).registerReceiver(yourBroadcastListener, filter);
     * }
     * </pre>
     */
    @SuppressWarnings("unused")
    public static final String LAUNCHED_FROM_NOTIFICATION_INTENT = "io.teak.sdk.Teak.intent.LAUNCHED_FROM_NOTIFICATION";

    /**
     * Intent action used by Teak to notify you that the a reward claim attempt has occured.
     * <p/>
     * You can listen for this using a {@link BroadcastReceiver} and the {@link LocalBroadcastManager}.
     * <pre>
     * {@code
     *     IntentFilter filter = new IntentFilter();
     *     filter.addAction(Teak.REWARD_CLAIM_ATTEMPT);
     *     LocalBroadcastManager.getInstance(context).registerReceiver(yourBroadcastListener, filter);
     * }
     * </pre>
     */
    @SuppressWarnings("unused")
    public static final String REWARD_CLAIM_ATTEMPT = "io.teak.sdk.Teak.intent.REWARD_CLAIM_ATTEMPT";

    ///// BroadcastReceiver

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // TODO: getInstance() ?
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.onReceive(context, intent);
                }
            });
        }
    }

    ///// Purchase Code

    // Called by Unity integration
    @SuppressWarnings("unused")
    private static void openIABPurchaseSucceeded(String json) {
        try {
            JSONObject purchase = new JSONObject(json);
            Teak.log.i("purchase.open_iab", Helpers.jsonToMap(purchase));

            final JSONObject originalJson = new JSONObject(purchase.getString("originalJson"));
            if (Instance != null) {
                asyncExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        Instance.purchaseSucceeded(originalJson);
                    }
                });
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    // Called by Unity integration
    @SuppressWarnings("unused")
    private static void prime31PurchaseSucceeded(String json) {
        try {
            final JSONObject originalJson = new JSONObject(json);
            Teak.log.i("purchase.prime_31", Helpers.jsonToMap(originalJson));

            if (Instance != null) {
                asyncExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        Instance.purchaseSucceeded(originalJson);
                    }
                });
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    // Called by Unity integration
    @SuppressWarnings("unused")
    private static void pluginPurchaseFailed(final int errorCode) {
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.purchaseFailed(errorCode);
                }
            });
        }
    }

    // Called by onActivityResult, as well as via reflection/directly in external purchase
    // activity code.
    public static void checkActivityResultForPurchase(final int resultCode, final Intent data) {
        if (Instance != null) {
            asyncExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    Instance.checkActivityResultForPurchase(resultCode, data);
                }
            });
        }
    }

    ///// Logging

    public static int jsonLogIndentation = 0;
    public static io.teak.sdk.Log log = new io.teak.sdk.Log(Teak.LOG_TAG, Teak.jsonLogIndentation);

    public static String formatJSONForLogging(JSONObject obj) throws JSONException {
        if (Teak.jsonLogIndentation > 0) {
            return obj.toString(Teak.jsonLogIndentation);
        } else {
            return obj.toString();
        }
    }

    ///// Give SDK wrappers the ability to delay deep link resolution

    public static Future<Void> waitForDeepLink;
    static {
        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(SessionStateEvent.Type) && ((SessionStateEvent)event).state == Session.State.Created) {

                    // Use thread instead of executor here, since this could block for a while
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (Teak.waitForDeepLink != null) {
                                    Teak.waitForDeepLink.get();
                                }
                            } catch (Exception ignored) {
                            }

                            // TODO: Is there a more general event that should be used here?
                            TeakEvent.postEvent(new DeepLinksReadyEvent());
                        }
                    }).start();
                }
            }
        });
    }

    ///// Configuration

    public static final String PREFERENCES_FILE = "io.teak.sdk.Preferences";

    ///// Data Members

    public static TeakInstance Instance;

    private static ExecutorService asyncExecutor = Executors.newCachedThreadPool();
}
