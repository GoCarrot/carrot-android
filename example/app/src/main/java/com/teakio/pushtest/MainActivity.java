package com.teakio.pushtest;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.TeakNotification;
import io.teak.sdk.event.PushNotificationEvent;

import android.view.View;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static final String LOG_TAG = "Teak.Example";

    // This is an example of a BroadcastReceiver that listens for launches from push notifications
    // and any rewards or deep links attached to them.
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Teak.LAUNCHED_FROM_NOTIFICATION_INTENT.equals(action)) {
                Bundle bundle = intent.getExtras();
                try {

                    HashMap<String, Object> teakReward = (HashMap<String, Object>) bundle.getSerializable("teakReward");
                    if (teakReward != null) {
                        Log.d(LOG_TAG, teakReward.toString());
                    }

                    if (bundle.getString("teakDeepLinkPath") != null) {
                        Log.d(LOG_TAG, bundle.getString("teakDeepLinkPath"));
                        HashMap<String, Object> teakDeepLinkQueryParameters = (HashMap<String, Object>) bundle.getSerializable("teakDeepLinkQueryParameters");
                        if (teakDeepLinkQueryParameters != null) {
                            Log.d(LOG_TAG, teakDeepLinkQueryParameters.toString());
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Teak.onCreate(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Register the BroadcastReceiver defined above to listen for notification launches
        IntentFilter filter = new IntentFilter();
        filter.addAction(Teak.LAUNCHED_FROM_NOTIFICATION_INTENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);

        Teak.identifyUser("demo-app-thingy-3");

        // Create a deep link route that opens the Google Play store to a specific SKU in your game
        final AppCompatActivity _this = this;
        Teak.registerDeepLink("/store/:sku", "Store", "Link directly to purchase an item", new Teak.DeepLink() {
            @Override
            public void call(Map<String, Object> parameters) {
                String sku = (String)parameters.get("sku");
                showPurchaseDialogForSku(sku);
            }
        });

        // Binding the in app billing service
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Call setIntent() for Teak
        setIntent(intent);
        super.onNewIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Call onActivityResult() for Teak
        Teak.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);

        // If your In App Purchase activity needs to be modified to call Teak, you can do this
        // easily without needing to import Teak (useful for other libraries, etc).
        /*
        try {
            Class<?> cls = Class.forName("io.teak.sdk.Teak");
            Method m = cls.getMethod("checkActivityResultForPurchase", int.class, Intent.class);
            m.invoke(null, resultCode, data);
        } catch (Exception ignored) {} */

        if (requestCode == 1001) {
            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

            if (resultCode == RESULT_OK) {
                try {
                    JSONObject jo = new JSONObject(purchaseData);
                    String sku = jo.getString("productId");
                    int response = mService.consumePurchase(3, getPackageName(), jo.getString("purchaseToken"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    ///////
    // Not required for Teak integration

    private void simulateNotification(Context context) {
        Intent intent = new Intent();
        intent.putExtra("teakNotifId", "fake-notif-id");
        intent.putExtra("version", "1");
        intent.putExtra("message", "Teak");

        // UI template
        JSONObject teak_notif_animated = new JSONObject();
        try {
            JSONObject animated = new JSONObject();
            animated.put("sprite_sheet", "assets:///777-animated-phone-it-in-snow.png");
            animated.put("rows", 4);
            animated.put("columns", 2);
            animated.put("width", 512);
            animated.put("height", 256);

            teak_notif_animated.put("text", "");
            //teak_notif_animated.put("view_animator", animated);
            //teak_notif_animated.put("left_image", "BUILTIN_APP_ICON");
            teak_notif_animated.put("left_image", "NONE");
        } catch (Exception ignored) {
        }

        JSONObject teak_big_notif_image_text = new JSONObject();
        try {
            JSONObject button0 = new JSONObject();
            button0.put("text", "The Button0");
            teak_big_notif_image_text.put("button0", button0);

            JSONObject button2 = new JSONObject();
            button0.put("text", "The Button2");
            //teak_big_notif_image_text.put("button2", button0);

            teak_big_notif_image_text.put("text", "teak_big_notif_image_text");
            teak_big_notif_image_text.put("notification_background", "assets:///pixelgrid_2000x2000.png");
        } catch (Exception ignored) {
        }

        // Display
        JSONObject display = new JSONObject();
        try {
            display.put("contentView", "teak_notif_animated");
            display.put("bigContentView", "teak_big_notif_image_text");
            display.put("teak_notif_animated", teak_notif_animated);
            display.put("teak_big_notif_image_text", teak_big_notif_image_text);
        } catch (Exception ignored) {
        }

        // Add display to intent
        intent.putExtra("display", display.toString());

        TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Received, context, intent));
    }

    private void showPurchaseDialogForSku(final String sku) {
        final AppCompatActivity _this = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bundle buyIntentBundle = mService.getBuyIntent(3, _this.getPackageName(), sku, "inapp", "");
                    PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                    _this.startIntentSenderForResult(pendingIntent.getIntentSender(), 1001, new Intent(), 0, 0, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mService != null) {
            unbindService(mServiceConn);
        }
    }

    public void testNotification(View view) {
        // Schedule Notification
        //scheduleTestNotification("Animated", "Default text", "5");

        // Simulate Notification
        final Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        this.startActivity(i);

        final AppCompatActivity _this = this;
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                simulateNotification(_this);
            }
        }, 1000);
    }

    public void makePurchase(View view) {
        showPurchaseDialogForSku("com.teakio.pushtest.dollar");
    }

    public void crashApp(View view) {
        throw new RuntimeException("I crashed the app!");
    }

    public void integrationTestTimeout(String timeout) {
        try {
            Class c = Class.forName("io.teak.sdk.Session");
            Field field = c.getDeclaredField("SAME_SESSION_TIME_DELTA");
            field.setAccessible(true);
            field.set(null, Integer.parseInt(timeout));
        } catch (Exception e) {
            Log.e(LOG_TAG, Log.getStackTraceString(e));
        }
    }

    public void scheduleTestNotification(String id, String defaultText, String delay) {
        TeakNotification.scheduleNotification(id, defaultText, Integer.parseInt(delay));
    }

    static IInAppBillingService mService;
    ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);

            // Clear inventory
            if (true) {
                try {
                    Bundle ownedItems = mService.getPurchases(3, getPackageName(), "inapp", null);
                    int response = ownedItems.getInt("RESPONSE_CODE");
                    if (response == 0) {
                        ArrayList<String>  purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");

                        for (int i = 0; i < purchaseDataList.size(); ++i) {
                            String purchaseData = purchaseDataList.get(i);
                            Log.d(LOG_TAG, purchaseData);
                            try {
                                JSONObject purchase = new JSONObject(purchaseData);
                                mService.consumePurchase(3, getPackageName(), purchase.getString("purchaseToken"));
                            } catch (Exception e) {
                                Log.e(LOG_TAG, Log.getStackTraceString(e));
                            }

                            // do something with this purchase information
                            // e.g. display the updated list of products owned by user
                            //response =
                        }

                        // if continuationToken != null, call getPurchases again
                        // and pass in the token to retrieve more items
                    }

                } catch (Exception e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                }
            }
        }
    };
}
