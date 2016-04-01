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

import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.content.ServiceConnection;

import android.content.pm.ResolveInfo;

import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import android.util.Log;

import java.util.List;
import java.util.ArrayList;

import java.lang.reflect.Method;

import org.json.JSONException;

class GooglePlay implements IStore {
    Object mService;
    ServiceConnection mServiceConn;
    Context mContext;
    boolean mDisposed = false;

    public static final String ITEM_TYPE_INAPP = "inapp";

    public static final int BILLING_RESPONSE_RESULT_OK = 0;
    public static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
    public static final int BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2;
    public static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
    public static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4;
    public static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5;
    public static final int BILLING_RESPONSE_RESULT_ERROR = 6;
    public static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
    public static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8;

    public static final String RESPONSE_CODE = "RESPONSE_CODE";
    public static final String RESPONSE_GET_SKU_DETAILS_LIST = "DETAILS_LIST";
    public static final String RESPONSE_BUY_INTENT = "BUY_INTENT";
    public static final String RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    public static final String RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE";
    public static final String RESPONSE_INAPP_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
    public static final String RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    public static final String RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    public static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

    public static final String GET_SKU_DETAILS_ITEM_LIST = "ITEM_ID_LIST";
    public static final String GET_SKU_DETAILS_ITEM_TYPE_LIST = "ITEM_TYPE_LIST";

    public void init(Context context) {
        mContext = context.getApplicationContext();

        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (Teak.isDebug) {
                    Log.d(Teak.LOG_TAG, "Billing service disconnected.");
                }
                mService = null;
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (mDisposed) return;
                if (Teak.isDebug) {
                    Log.d(Teak.LOG_TAG, "Billing service connected.");
                }

                try {
                    Class<?> cls = Class.forName("com.android.vending.billing.IInAppBillingService$Stub");
                    Method m = cls.getMethod("asInterface", IBinder.class);
                    mService = m.invoke(null, (Object) service);
                    Log.d(Teak.LOG_TAG, "Service: " + mService.toString());
                } catch (Exception e) {
                    Log.e(Teak.LOG_TAG, "Unable to use 'IInAppBillingService' via reflection. " + e.toString());
                    return;
                }

                String packageName = mContext.getPackageName();
                try {
                    if (Teak.isDebug) {
                        Log.d(Teak.LOG_TAG, "Checking for in-app billing 3 support.");
                    }

                    // check for in-app billing v3 support
                    Class<?> cls = Class.forName("com.android.vending.billing.IInAppBillingService");
                    Method m = cls.getMethod("isBillingSupported", int.class, String.class, String.class);
                    int response = ((Integer) m.invoke(mService, 3, packageName, ITEM_TYPE_INAPP)).intValue();
                    if (response != BILLING_RESPONSE_RESULT_OK) {
                        // "Error checking for billing v3 support."
                    } else {
                        if (Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "In-app billing version 3 supported for " + packageName);
                        }

                        // Hax
                        List<String> skus = new ArrayList<String>();
                        skus.add("io.teak.demo.angrybots.dollar2");
                        querySkuDetails(skus);
                    }
                }
                catch (/*Remote*/Exception e) { // HAX
                    // "RemoteException while setting up in-app billing."
                    e.printStackTrace();
                    return;
                }
            }
        };

        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        List<ResolveInfo> intentServices = mContext.getPackageManager().queryIntentServices(serviceIntent, 0);
        if (intentServices != null && !intentServices.isEmpty()) {
            // service available to handle that Intent
            mContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
        }
        else {
            // no service available to handle that Intent
            // "Billing service unavailable on device."
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

    public int querySkuDetails(String itemType, List<String> skuList) throws RemoteException, JSONException {
        if (skuList.size() == 0) {
            return BILLING_RESPONSE_RESULT_OK;
        }

        // Split the sku list in blocks of no more than 20 elements.
        ArrayList<ArrayList<String>> packs = new ArrayList<ArrayList<String>>();
        ArrayList<String> tempList;
        int n = skuList.size() / 20;
        int mod = skuList.size() % 20;
        for (int i = 0; i < n; i++) {
            tempList = new ArrayList<String>();
            for (String s : skuList.subList(i * 20, i * 20 + 20)) {
                tempList.add(s);
            }
            packs.add(tempList);
        }
        if (mod != 0) {
            tempList = new ArrayList<String>();
            for (String s : skuList.subList(n * 20, n * 20 + mod)) {
                tempList.add(s);
            }
            packs.add(tempList);
        }

        try {
            Class<?> cls = Class.forName("com.android.vending.billing.IInAppBillingService");
            Method m = cls.getMethod("getSkuDetails", int.class, String.class, String.class, Bundle.class);
            for (ArrayList<String> skuPartList : packs) {
                Bundle querySkus = new Bundle();
                querySkus.putStringArrayList(GET_SKU_DETAILS_ITEM_LIST, skuPartList);

                Bundle skuDetails = (Bundle) m.invoke(mService, 3, mContext.getPackageName(), ITEM_TYPE_INAPP, querySkus);

                if (!skuDetails.containsKey(RESPONSE_GET_SKU_DETAILS_LIST)) {
                    int response = getResponseCodeFromBundle(skuDetails);
                    if (response != BILLING_RESPONSE_RESULT_OK) {
                        Log.e(Teak.LOG_TAG, "getSkuDetails() failed: " + response);
                        return response;
                    } else {
                        Log.e(Teak.LOG_TAG, "getSkuDetails() returned a bundle with neither an error nor a detail list.");
                        return -1;
                    }
                }

                ArrayList<String> responseList = skuDetails.getStringArrayList(RESPONSE_GET_SKU_DETAILS_LIST);

                for (String thisResponse : responseList) {
                    Log.d(Teak.LOG_TAG, "Got sku details: " + thisResponse);
                }
            }
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, "Reflection error: " + e.toString());
        }

        return BILLING_RESPONSE_RESULT_OK;
    }

    int getResponseCodeFromBundle(Bundle b) {
        Object o = b.get(RESPONSE_CODE);
        if (o == null) {
            return BILLING_RESPONSE_RESULT_OK;
        }
        else if (o instanceof Integer) return ((Integer)o).intValue();
        else if (o instanceof Long) return (int)((Long)o).longValue();
        else {
            Log.e(Teak.LOG_TAG, "Unexpected type for bundle response code.");
            Log.e(Teak.LOG_TAG, o.getClass().getName());
            throw new RuntimeException("Unexpected type for bundle response code: " + o.getClass().getName());
        }
    }
}
