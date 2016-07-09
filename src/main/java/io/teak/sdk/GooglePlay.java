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
package io.teak.sdk;

import android.app.Activity;

import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.content.ServiceConnection;

import android.content.pm.ResolveInfo;

import android.os.Bundle;
import android.os.IBinder;

import android.util.Log;

import java.util.List;
import java.util.ArrayList;

import java.lang.reflect.Method;

import org.json.JSONObject;

@SuppressWarnings("unused")
class GooglePlay implements IStore {
    Object mService;
    ServiceConnection mServiceConn;
    Context mContext;
    boolean mDisposed = false;

    public static final String ITEM_TYPE_INAPP = "inapp";
    public static final String ITEM_TYPE_SUBS = "subs";

    public static final int BILLING_RESPONSE_RESULT_OK = 0;
    //public static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
    //public static final int BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2;
    //public static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
    //public static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4;
    //public static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5;
    //public static final int BILLING_RESPONSE_RESULT_ERROR = 6;
    //public static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
    //public static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8;

    public static final String RESPONSE_CODE = "RESPONSE_CODE";
    public static final String RESPONSE_GET_SKU_DETAILS_LIST = "DETAILS_LIST";
    //public static final String RESPONSE_BUY_INTENT = "BUY_INTENT";
    public static final String RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    public static final String RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE";
    //public static final String RESPONSE_INAPP_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
    //public static final String RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    //public static final String RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    //public static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

    public static final String GET_SKU_DETAILS_ITEM_LIST = "ITEM_ID_LIST";
    //public static final String GET_SKU_DETAILS_ITEM_TYPE_LIST = "ITEM_TYPE_LIST";

    public void init(Context context) {
        mContext = context.getApplicationContext();

        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (Teak.isDebug) {
                    Log.d(Teak.LOG_TAG, "Google Play Billing service disconnected.");
                }
                mService = null;
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (mDisposed) return;
                if (Teak.isDebug) {
                    Log.d(Teak.LOG_TAG, "Google Play Billing service connected.");
                }

                try {
                    Class<?> cls = Class.forName("com.android.vending.billing.IInAppBillingService$Stub");
                    Method m = cls.getMethod("asInterface", IBinder.class);
                    mService = m.invoke(null, (Object) service);
                } catch (Exception e) {
                    Log.e(Teak.LOG_TAG, "Unable to use 'IInAppBillingService' via reflection. " + Log.getStackTraceString(e));
                    Teak.sdkRaven.reportException(e);
                    return;
                }

                String packageName = mContext.getPackageName();
                try {
                    if (Teak.isDebug) {
                        Log.d(Teak.LOG_TAG, "Checking for Google Play in-app billing 3 support.");
                    }

                    // check for in-app billing v3 support
                    Class<?> cls = Class.forName("com.android.vending.billing.IInAppBillingService");
                    Method m = cls.getMethod("isBillingSupported", int.class, String.class, String.class);
                    int response = (Integer) m.invoke(mService, 3, packageName, ITEM_TYPE_INAPP);
                    if (response != BILLING_RESPONSE_RESULT_OK) {
                        Log.e(Teak.LOG_TAG, "Error checking for Google Play billing v3 support.");
                    } else {
                        if (Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "Google Play In-app billing version 3 supported for " + packageName);
                        }
                    }

                    // Check for v5 subscriptions support. This is needed for
                    // getBuyIntentToReplaceSku which allows for subscription update
                    response = (Integer) m.invoke(mService, 5, packageName, ITEM_TYPE_SUBS);
                    if (response == BILLING_RESPONSE_RESULT_OK) {
                        if (Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "Google Play Subscription re-signup available.");
                            Log.d(Teak.LOG_TAG, "Google Play Subscriptions available.");
                        }
                    } else {
                        if (Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "Google Play Subscription re-signup not available.");
                        }
                        // check for v3 subscriptions support
                        response = (Integer) m.invoke(mService, 3, packageName, ITEM_TYPE_SUBS);
                        if (response == BILLING_RESPONSE_RESULT_OK) {
                            if (Teak.isDebug) {
                                Log.d(Teak.LOG_TAG, "Google Play Subscriptions available.");
                            }
                        } else {
                            if (Teak.isDebug) {
                                Log.d(Teak.LOG_TAG, "Google Play Subscriptions NOT available. Response: " + response);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(Teak.LOG_TAG, "Error working with InAppBillingService: " + Log.getStackTraceString(e));
                    Teak.sdkRaven.reportException(e);
                }
            }
        };

        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        List<ResolveInfo> intentServices = mContext.getPackageManager().queryIntentServices(serviceIntent, 0);
        if (intentServices != null && !intentServices.isEmpty()) {
            mContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
        } else {
            Log.e(Teak.LOG_TAG, "Google Play Billing service unavailable on device.");
        }
    }

    public boolean ignorePluginPurchaseEvents() {
        return false;
    }

    public void onActivityResumed() {
        // Empty
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

    public JSONObject querySkuDetails(String sku) {
        JSONObject ret = querySkuDetails(ITEM_TYPE_INAPP, sku);
        if (ret == null) {
            ret = querySkuDetails(ITEM_TYPE_SUBS, sku);
        }
        return ret;
    }

    private JSONObject querySkuDetails(String itemType, String sku) {
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
                    Log.e(Teak.LOG_TAG, "getSkuDetails() failed: " + response);
                    return null;
                } else {
                    Log.e(Teak.LOG_TAG, "getSkuDetails() returned a bundle with neither an error nor a detail list.");
                    return null;
                }
            }

            ArrayList<String> responseList = skuDetails.getStringArrayList(RESPONSE_GET_SKU_DETAILS_LIST);

            if (responseList != null && responseList.size() == 1) {
                JSONObject ret = new JSONObject(responseList.get(0));
                if (Teak.isDebug) {
                    Log.d(Teak.LOG_TAG, "SKU Details: " + ret.toString(2));
                }
                return ret;
            } else {
                Log.e(Teak.LOG_TAG, "Mismatched input/output length for getSkuDetails().");
            }
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, "Reflection error: " + Log.getStackTraceString(e));
            Teak.sdkRaven.reportException(e);
        }

        return null;
    }

    int getResponseCodeFromBundle(Bundle b) {
        Object o = b.get(RESPONSE_CODE);
        if (o == null) {
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) return (Integer) o;
        else if (o instanceof Long) return (int) ((Long) o).longValue();
        else {
            Log.e(Teak.LOG_TAG, "Unexpected type for bundle response code.");
            Log.e(Teak.LOG_TAG, o.getClass().getName());
            throw new RuntimeException("Unexpected type for bundle response code: " + o.getClass().getName());
        }
    }

    int getResponseCodeFromIntent(Intent i) {
        Object o = i.getExtras().get(RESPONSE_CODE);
        if (o == null) {
            Log.e(Teak.LOG_TAG, "Intent with no response code, assuming OK (known Google issue)");
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) return (Integer) o;
        else if (o instanceof Long) return (int) ((Long) o).longValue();
        else {
            Log.e(Teak.LOG_TAG, "Unexpected type for intent response code.");
            Log.e(Teak.LOG_TAG, o.getClass().getName());
            throw new RuntimeException("Unexpected type for intent response code: " + o.getClass().getName());
        }
    }

    public void checkActivityResultForPurchase(int resultCode, Intent data) {
        String purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);
        String dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);

        Log.d(Teak.LOG_TAG, "Checking activity result for purchase.");

        // Check for purchase activity result
        if (purchaseData != null && dataSignature != null) {
            int responseCode = getResponseCodeFromIntent(data);

            if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
                try {
                    JSONObject json = new JSONObject(purchaseData);
                    Teak.purchaseSucceeded(json);
                } catch (Exception e) {
                    Log.e(Teak.LOG_TAG, "Failed to convert purchase data to JSON.");
                    Teak.sdkRaven.reportException(e);
                }
            } else {
                Teak.purchaseFailed(responseCode);
            }
        } else {
            Log.d(Teak.LOG_TAG, "No purchase found in activity result.");
        }
    }
}
