package io.teak.app.test;

import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SdkSuppress;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.teak.sdk.IObjectFactory;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.core.ITeakCore;
import io.teak.sdk.core.TeakCore;
import io.teak.sdk.event.PurchaseEvent;
import io.teak.sdk.event.PurchaseFailedEvent;
import io.teak.sdk.io.DefaultAndroidDeviceInfo;
import io.teak.sdk.io.DefaultAndroidNotification;
import io.teak.sdk.io.IAndroidDeviceInfo;
import io.teak.sdk.io.IAndroidNotification;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.push.GCMPushProvider;
import io.teak.sdk.push.IPushProvider;
import io.teak.sdk.store.GooglePlay;
import io.teak.sdk.store.IStore;

import static junit.framework.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class Purchase {
    @Test
    @SdkSuppress(minSdkVersion = 16)
    public void failPurchase() throws IntentSender.SendIntentException, RemoteException, InterruptedException, TimeoutException {
        // Launch the activity
        testRule.launchActivity(null);

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final UiDevice device = UiDevice.getInstance(instrumentation);

        // Bind the in app billing service
        final Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        serviceRule.bindService(serviceIntent, this.serviceConnection, Context.BIND_AUTO_CREATE);
        this.serviceConnectedLatch.await(500, TimeUnit.MILLISECONDS);
        assertNotNull(this.inAppBillingService);

        // Purchase invalid item
        // ("io.teak.app.test.sku.dollar" is valid)
        final Bundle buyIntentBundle = inAppBillingService.getBuyIntent(3, testRule.getActivity().getPackageName(), "io.teak.app.test.sku.FAKE_SKUS", "inapp", "");
        final PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
        assertNotNull(pendingIntent);
        testRule.getActivity().startIntentSenderForResult(pendingIntent.getIntentSender(), 1001, new Intent(), 0, 0, 0);

        // Click 'Ok'
        final BySelector continueButtonSelector = By.res("com.android.vending:id/continue_button");
        device.wait(Until.hasObject(continueButtonSelector), 10000);
        UiObject2 okButton = device.findObject(continueButtonSelector);
        Assert.assertNotNull(okButton);
        okButton.click();

        // PurchaseFailedEvent should be received
        verify(eventListener, timeout(5000).times(1)).eventRecieved(PurchaseFailedEvent.class, PurchaseFailedEvent.Type);
        //Thread.sleep(200000);
    }

    @Test
    @SdkSuppress(minSdkVersion = 16)
    public void succeedPurchase() throws IntentSender.SendIntentException, RemoteException, InterruptedException, TimeoutException {
        // Launch the activity
        testRule.launchActivity(null);

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final UiDevice device = UiDevice.getInstance(instrumentation);

        // Bind the in app billing service
        final Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        serviceRule.bindService(serviceIntent, this.serviceConnection, Context.BIND_AUTO_CREATE);
        this.serviceConnectedLatch.await(500, TimeUnit.MILLISECONDS);
        assertNotNull(this.inAppBillingService);

        // Purchase "io.teak.app.test.sku.dollar"
        final Bundle buyIntentBundle = inAppBillingService.getBuyIntent(3, testRule.getActivity().getPackageName(), "io.teak.app.test.sku.dollar", "inapp", "");
        final PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
        assertNotNull(pendingIntent);
        testRule.getActivity().startIntentSenderForResult(pendingIntent.getIntentSender(), 1001, new Intent(), 0, 0, 0);

        // Click 'BUY'
        final BySelector continueButtonSelector = By.res("com.android.vending:id/continue_button");
        device.wait(Until.hasObject(continueButtonSelector), 10000);
        final UiObject2 buyButton = device.findObject(continueButtonSelector);
        assertNotNull(buyButton);
        buyButton.click();

        // PurchaseEvent should be received
        verify(eventListener, timeout(5000).times(1)).eventRecieved(PurchaseEvent.class, PurchaseEvent.Type);
    }

    //////

    @Rule
    public ServiceTestRule serviceRule = new ServiceTestRule();

    private CountDownLatch serviceConnectedLatch = new CountDownLatch(1);
    private IInAppBillingService inAppBillingService;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            inAppBillingService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            inAppBillingService = IInAppBillingService.Stub.asInterface(service);

            // Clear inventory
            consumeAllPurchasedItems();

            // Ready
            serviceConnectedLatch.countDown();
        }
    };

    @After
    public void consumeAllPurchasedItems() {
        try {
            Bundle ownedItems = inAppBillingService.getPurchases(3, testRule.getActivity().getPackageName(), "inapp", null);
            int response = ownedItems.getInt("RESPONSE_CODE");
            if (response == 0) {
                ArrayList<String> purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                if (purchaseDataList != null) {
                    for (int i = 0; i < purchaseDataList.size(); ++i) {
                        String purchaseData = purchaseDataList.get(i);
                        Log.d("PurchaseTesting", purchaseData);
                        try {
                            JSONObject purchase = new JSONObject(purchaseData);
                            inAppBillingService.consumePurchase(3, testRule.getActivity().getPackageName(), purchase.getString("purchaseToken"));
                        } catch (Exception e) {
                            Log.e("PurchaseTesting", Log.getStackTraceString(e));
                        }

                        // do something with this purchase information
                        // e.g. display the updated list of products owned by user
                        //response =
                    }
                }

                // if continuationToken != null, call getPurchases again
                // and pass in the token to retrieve more items
            }
        } catch (Exception e) {
            Log.e("PurchaseTesting", Log.getStackTraceString(e));
        }
    }

    @AfterClass
    public static void stopEventProcessingThread() {
        TeakEvent.postEvent(TeakEvent.StopEvent);
    }

    private TestTeakEventListener eventListener;
    @Rule
    public ActivityTestRule<MainActivity> testRule = new ActivityTestRule<MainActivity>(MainActivity.class, false, false) {
        @Override
        protected void beforeActivityLaunched() {
            super.beforeActivityLaunched();

            // Reset Teak.Instance
            Teak.Instance = null;

            // Create and add an easily mockable TeakEvent.EventListener
            if (eventListener != null) {
                TeakEvent.removeEventListener(eventListener);
            }
            eventListener = spy(TestTeakEventListener.class);
            TeakEvent.addEventListener(eventListener);

            MainActivity.whateverFactory = new IObjectFactory() {
                private IStore store;
                private IAndroidDeviceInfo androidDeviceInfo;
                private IPushProvider pushProvider;
                private IAndroidResources androidResources;

                @NonNull
                @Override
                public IStore getIStore() {
                    if (this.store == null) {
                        this.store = new GooglePlay();
                    }
                    return this.store;
                }

                @NonNull
                @Override
                public IAndroidResources getAndroidResources() {
                    if (this.androidResources == null) {
                        this.androidResources = mock(io.teak.sdk.io.IAndroidResources.class);
                        when(androidResources.getStringResource(AppConfiguration.TEAK_APP_ID)).thenReturn("1919749661621253");
                        when(androidResources.getStringResource(AppConfiguration.TEAK_API_KEY)).thenReturn("2cd84c8899833f08c48aca2e1909b6c5");
                    }
                    return androidResources;
                }

                @NonNull
                @Override
                public IAndroidDeviceInfo getAndroidDeviceInfo() {
                    if (this.androidDeviceInfo == null) {
                        this.androidDeviceInfo = new DefaultAndroidDeviceInfo(testRule.getActivity());
                    }
                    return this.androidDeviceInfo;
                }

                @NonNull
                @Override
                public IPushProvider getPushProvider() {
                    if (this.pushProvider == null) {
                        this.pushProvider = new GCMPushProvider(testRule.getActivity());
                    }
                    return pushProvider;
                }

                @NonNull
                @Override
                public IAndroidNotification getAndroidNotification() {
                    return DefaultAndroidNotification.get(testRule.getActivity());
                }

                @NonNull
                @Override
                public ITeakCore getTeakCore() {
                    return TeakCore.get(testRule.getActivity());
                }
            };
        }

        @Override
        protected void afterActivityLaunched() {
            super.afterActivityLaunched();
        }

        @Override
        protected void afterActivityFinished() {
            super.afterActivityFinished();
        }
    };
}
