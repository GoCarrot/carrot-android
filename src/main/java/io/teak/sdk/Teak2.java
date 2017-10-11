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

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.teak.sdk.Helpers._;

/**
 * Teak
 */
public class Teak2 extends BroadcastReceiver {
    private static final String LOG_TAG = "Teak";

    /**
     * Version of the Teak SDK.
     */
    public static final String SDKVersion = io.teak.sdk.BuildConfig.VERSION_NAME;

    /**
     * Force debug print on/off.
     */
    public static boolean forceDebug;

    /**
     * Initialize Teak and tell it to listen to the lifecycle events of {@link Activity}.
     * <p/>
     * <p>Call this function from the {@link Activity#onCreate} function of your <code>Activity</code>
     * <b>before</b> the call to <code>super.onCreate()</code></p>
     *
     * @param activity The main <code>Activity</code> of your app.
     */
    public static void onCreate(@NonNull Activity activity) {
        onCreate(activity, null);
    }

    /**
     * Used for internal testing.
     *
     * @param activity The main <code>Activity</code> of your app.
     * @param objectFactory Teak Object Factory to use, or null for default.
     */
    public static void onCreate(@NonNull Activity activity, @Nullable ObjectFactory objectFactory) {
        if (objectFactory == null) {
            objectFactory = new DefaultObjectFactory();
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
    public static void onActivityResult(@SuppressWarnings("unused") int requestCode, int resultCode, Intent data) {
        Teak.log.i("lifecycle", _.h("callback", "onActivityResult"));

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
        throw new RuntimeException("Teak.onNewIntent is deprecated, call Activity.onNewIntent() instead.");
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
        } else {
            // TODO: Throw exception for integration help?
        }
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
                        Instance.pluginPurchaseSucceeded(originalJson);
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
                        Instance.pluginPurchaseSucceeded(originalJson);
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
                    Instance.pluginPurchaseFailed(errorCode);
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

    ///// Data Members

    public static TeakInstance Instance;
    public static Future<Void> waitForDeepLink;
    private static ExecutorService asyncExecutor = Executors.newCachedThreadPool();
}
