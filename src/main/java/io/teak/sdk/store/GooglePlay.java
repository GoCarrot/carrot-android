/* Teak -- Copyright (C) 2016 GoCarrot Inc.
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
package io.teak.sdk.store;

import android.app.Activity;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.content.ServiceConnection;

import android.content.pm.ResolveInfo;

import android.os.Bundle;
import android.os.IBinder;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import java.lang.reflect.Method;
import java.util.Map;

import org.json.JSONObject;

import io.teak.sdk.Helpers;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.PurchaseEvent;
import io.teak.sdk.event.PurchaseFailedEvent;

@SuppressWarnings("unused")
class GooglePlay implements IStore {
    private Object mService;
    private ServiceConnection mServiceConn;
    private Context mContext;
    private boolean mDisposed = false;

    private static final String ITEM_TYPE_INAPP = "inapp";
    private static final String ITEM_TYPE_SUBS = "subs";

    private static final int BILLING_RESPONSE_RESULT_OK = 0;
    //private static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
    //private static final int BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2;
    //private static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
    //private static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4;
    //public static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5;
    //private static final int BILLING_RESPONSE_RESULT_ERROR = 6;
    //private static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
    //private static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8;

    private static final String RESPONSE_CODE = "RESPONSE_CODE";
    private static final String RESPONSE_GET_SKU_DETAILS_LIST = "DETAILS_LIST";
    //private static final String RESPONSE_BUY_INTENT = "BUY_INTENT";
    private static final String RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    private static final String RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE";
    //private static final String RESPONSE_INAPP_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
    //private static final String RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    //private static final String RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    //private static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

    private static final String GET_SKU_DETAILS_ITEM_LIST = "ITEM_ID_LIST";
    //private static final String GET_SKU_DETAILS_ITEM_TYPE_LIST = "ITEM_TYPE_LIST";

    public void init(Context context) {
        mContext = context;

        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                mService = null;
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (mDisposed) return;

                try {
                    Class<?> cls = Class.forName("com.android.vending.billing.IInAppBillingService$Stub");
                    Method m = cls.getMethod("asInterface", IBinder.class);
                    mService = m.invoke(null, (Object) service);
                } catch (Exception e) {
                    Teak.log.exception(e);
                    return;
                }

                String packageName = mContext.getPackageName();
                try {
                    // check for in-app billing v3 support
                    Class<?> cls = Class.forName("com.android.vending.billing.IInAppBillingService");
                    Method m = cls.getMethod("isBillingSupported", int.class, String.class, String.class);
                    int response = (Integer) m.invoke(mService, 3, packageName, ITEM_TYPE_INAPP);
                    if (response != BILLING_RESPONSE_RESULT_OK) {
                        Teak.log.e("google_play", "Error checking for Google Play billing v3 support.");
                    }

                    // Check for v5 subscriptions support. This is needed for
                    // getBuyIntentToReplaceSku which allows for subscription update
                    response = (Integer) m.invoke(mService, 5, packageName, ITEM_TYPE_SUBS);
                    //noinspection StatementWithEmptyBody
                    if (response == BILLING_RESPONSE_RESULT_OK) {
                        // Subscription v5 available
                    } else {
                        // Subscription v5 not available

                        // check for v3 subscriptions support
                        response = (Integer) m.invoke(mService, 3, packageName, ITEM_TYPE_SUBS);
                        //noinspection StatementWithEmptyBody
                        if (response == BILLING_RESPONSE_RESULT_OK) {
                            // Subscription v3 available
                        } else {
                            // Subscriptsion v3 not available
                        }
                    }
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            }
        };

        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        List<ResolveInfo> intentServices = mContext.getPackageManager().queryIntentServices(serviceIntent, 0);
        if (intentServices != null && !intentServices.isEmpty()) {
            mContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
        } else {
            Teak.log.e("google_play", "Google Play Billing service unavailable on device.");
        }
    }

    public void launchPurchaseFlowForSKU(String sku) {
        try {
            Class<?> cls = Class.forName("com.android.vending.billing.IInAppBillingService");
            Method m = cls.getMethod("getBuyIntent", int.class, String.class, String.class, String.class);

            Bundle buyIntentBundle = (Bundle) m.invoke(mService, 3, mContext.getPackageName(), "inapp", sku);
            PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
            if (pendingIntent != null) {
                ((Activity)mContext).startIntentSenderForResult(pendingIntent.getIntentSender(), 1001, new Intent(), 0, 0, 0);
            }
        } catch (Exception ignored) {
        }
    }

    public void dispose() {
        if (mServiceConn != null) {
            if (mContext != null) mContext.unbindService(mServiceConn);
        }
        mDisposed = true;
        mContext = null;
        mServiceConn = null;
        mService = null;
    }

    public void processPurchaseJson(JSONObject originalJson) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("purchase_token", originalJson.get("purchaseToken"));
            payload.put("purchase_time", originalJson.get("purchaseTime"));
            payload.put("product_id", originalJson.get("productId"));
            if (originalJson.has("orderId")) {
                payload.put("order_id", originalJson.get("orderId"));
            }

            JSONObject skuDetails = querySkuDetails((String) payload.get("product_id"));
            if (skuDetails != null) {
                if (skuDetails.has("price_amount_micros")) {
                    payload.put("price_currency_code", skuDetails.getString("price_currency_code"));
                    payload.put("price_amount_micros", skuDetails.getString("price_amount_micros"));
                } else if (skuDetails.has("price_string")) {
                    payload.put("price_string", skuDetails.getString("price_string"));
                }
            }

            TeakEvent.postEvent(new PurchaseEvent(payload));
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    private JSONObject querySkuDetails(String sku) {
        JSONObject ret = querySkuDetails(ITEM_TYPE_INAPP, sku);
        if (ret == null) {
            ret = querySkuDetails(ITEM_TYPE_SUBS, sku);
        }
        return ret;
    }

    private JSONObject querySkuDetails(String itemType, String sku) {
        if (mService == null) return null; // Sentry bug: TEAK-SDK-9

        try {
            ArrayList<String> skuList = new ArrayList<>();
            skuList.add(sku);
            Class<?> cls = Class.forName("com.android.vending.billing.IInAppBillingService");
            Method m = cls.getMethod("getSkuDetails", int.class, String.class, String.class, Bundle.class);

            Bundle querySkus = new Bundle();
            querySkus.putStringArrayList(GET_SKU_DETAILS_ITEM_LIST, skuList);

            Bundle skuDetails = (Bundle) m.invoke(mService, 3, mContext.getPackageName(), itemType, querySkus);

            if (!skuDetails.containsKey(RESPONSE_GET_SKU_DETAILS_LIST)) {
                int response = getResponseCodeFromBundle(skuDetails);
                if (response != BILLING_RESPONSE_RESULT_OK) {
                    Teak.log.e("google_play", "getSkuDetails() failed: " + response);
                    return null;
                } else {
                    Teak.log.e("google_play", "getSkuDetails() returned a bundle with neither an error nor a detail list.");
                    return null;
                }
            }

            ArrayList<String> responseList = skuDetails.getStringArrayList(RESPONSE_GET_SKU_DETAILS_LIST);

            if (responseList != null && responseList.size() == 1) {
                JSONObject ret = new JSONObject(responseList.get(0));
                Teak.log.i("google_play", "SKU Details.", Helpers.jsonToMap(ret));
                return ret;
            } else {
                Teak.log.e("google_play", "Mismatched input/output length for getSkuDetails().");
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }

        return null;
    }

    private int getResponseCodeFromBundle(Bundle b) {
        Object o = b.get(RESPONSE_CODE);
        if (o == null) {
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) return (Integer) o;
        else if (o instanceof Long) return (int) ((Long) o).longValue();
        else {
            Teak.log.e("google_play", "Unexpected type for bundle response code.", Helpers.mm.h("class", o.getClass().getName()));
            throw new RuntimeException("Unexpected type for bundle response code: " + o.getClass().getName());
        }
    }

    private int getResponseCodeFromIntent(Intent i) {
        Object o = i.getExtras().get(RESPONSE_CODE);
        if (o == null) {
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) return (Integer) o;
        else if (o instanceof Long) return (int) ((Long) o).longValue();
        else {
            Teak.log.e("google_play", "Unexpected type for bundle response code.", Helpers.mm.h("class", o.getClass().getName()));
            throw new RuntimeException("Unexpected type for intent response code: " + o.getClass().getName());
        }
    }

    public void checkActivityResultForPurchase(int resultCode, Intent data) {
        String purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);
        String dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);

        Teak.log.i("google_play", "Checking activity result for purchase.");

        // Check for purchase activity result
        if (purchaseData != null && dataSignature != null) {
            int responseCode = getResponseCodeFromIntent(data);

            if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
                try {
                    processPurchaseJson(new JSONObject(purchaseData));
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            } else {
                TeakEvent.postEvent(new PurchaseFailedEvent(responseCode));
            }
        } else {
            Teak.log.i("google_play", "Checking activity result for purchase.");
        }
    }
}
