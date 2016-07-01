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

import android.util.Log;

import android.content.Intent;
import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import org.json.JSONObject;

@SuppressWarnings("unused")
class Amazon implements IStore {
    HashMap<String, String> skuPriceMap;
    HashMap<String, Object> skuDetailsRequestMap;

    public void init(Context context) {
        skuDetailsRequestMap = new HashMap<>();
        skuPriceMap = new HashMap<>();
        try {
            Class<?> purchasingListenerClass = Class.forName("com.amazon.device.iap.PurchasingListener");
            InvocationHandler handler = new PurchasingListenerInvocationHandler();
            Object proxy = Proxy.newProxyInstance(purchasingListenerClass.getClassLoader(), new Class[]{purchasingListenerClass}, handler);

            Class<?> purchasingServiceClass = Class.forName("com.amazon.device.iap.PurchasingService");
            Method m = purchasingServiceClass.getMethod("registerListener", Context.class, purchasingListenerClass);
            m.invoke(null, context.getApplicationContext(), proxy);

            if (Teak.isDebug) {
                Field sandbox = purchasingServiceClass.getDeclaredField("IS_SANDBOX_MODE");

                Log.d(Teak.LOG_TAG, "Amazon In-App Purchasing 2.0 registered.");
                Log.d(Teak.LOG_TAG, "   Sandbox Mode: " + sandbox.getBoolean(null));
            }
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, "Reflection error: " + Log.getStackTraceString(e));
            Teak.sdkRaven.reportException(e);
        }
    }

    public void onActivityResumed() {
        // Get user data and insert it into the dynamic common payload
        try {
            Class<?> purchasingServiceClass = Class.forName("com.amazon.device.iap.PurchasingService");
            Method m = purchasingServiceClass.getMethod("getUserData");
            m.invoke(null);
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, "Reflection error: " + Log.getStackTraceString(e));
            Teak.sdkRaven.reportException(e);
        }
    }

    public void dispose() {
        // None
    }

    public JSONObject querySkuDetails(String sku) {
        try {
            Class<?> purchasingServiceClass = Class.forName("com.amazon.device.iap.PurchasingService");
            Method m = purchasingServiceClass.getMethod("getProductData", Set.class);
            HashSet<String> skus = new HashSet<>();
            skus.add(sku);
            Object requestId = m.invoke(null, skus);

            skuDetailsRequestMap.put(requestId.toString(), requestId);
            synchronized (requestId) {
                requestId.wait();
            }
            skuDetailsRequestMap.remove(requestId.toString());

            JSONObject ret = new JSONObject();
            ret.put("price_string", skuPriceMap.get(sku));
            return ret;
        } catch (Exception e) {
            Log.e(Teak.LOG_TAG, "Reflection error: " + Log.getStackTraceString(e));
            Teak.sdkRaven.reportException(e);
        }
        return null;
    }

    public void checkActivityResultForPurchase(int resultCode, Intent data) {
        // None
    }

    public boolean ignorePluginPurchaseEvents() {
        return true;
    }

    // com.amazon.device.iap.PurchasingListener
    class PurchasingListenerInvocationHandler implements InvocationHandler {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            // toString()
            if (method.getName().equals("toString")) {
                return "io.teak.sdk.Amazon$PurchasingListenerInvocationHandler";

                // onUserDataResponse()
            } else if (method.getName().equals("onUserDataResponse")) {
                Class<?> userDataResponseClass = Class.forName("com.amazon.device.iap.model.UserDataResponse");
                Method m = userDataResponseClass.getMethod("getRequestStatus");
                Object requestStatus = m.invoke(args[0]);

                Class<?> userDataResponseRequestStatusClass = Class.forName("com.amazon.device.iap.model.UserDataResponse$RequestStatus");
                Field success = userDataResponseRequestStatusClass.getDeclaredField("SUCCESSFUL");

                if (requestStatus.equals(success.get(null))) {
                    m = userDataResponseClass.getMethod("getUserData");
                    Object userData = m.invoke(args[0]);
                    Class<?> userDataClass = Class.forName("com.amazon.device.iap.model.UserData");
                    m = userDataClass.getMethod("getUserId");
                    String storeUserId = m.invoke(userData).toString();
                    Request.dynamicCommonPayload.put("store_user_id", storeUserId);
                    m = userDataClass.getMethod("getMarketplace");
                    String storeMarketplace = m.invoke(userData).toString();
                    Request.dynamicCommonPayload.put("store_marketplace", storeMarketplace);
                    if (Teak.isDebug) {
                        Log.d(Teak.LOG_TAG, "Amazon Store User Details retrieved:");
                        Log.d(Teak.LOG_TAG, "       User id: " + storeUserId);
                        Log.d(Teak.LOG_TAG, "   Marketplace: " + storeMarketplace);
                    }
                } else {
                    if (Teak.isDebug) {
                        Log.d(Teak.LOG_TAG, "Amazon Store user data query failed, or unsupported.");
                    }
                }

                // onPurchaseResponse()
            } else if (method.getName().equals("onPurchaseResponse")) {
                Class<?> purchaseResponseClass = Class.forName("com.amazon.device.iap.model.PurchaseResponse");
                Method m = purchaseResponseClass.getMethod("getRequestStatus");
                Object requestStatus = m.invoke(args[0]);

                Class<?> purchaseResponseRequestStatusClass = Class.forName("com.amazon.device.iap.model.PurchaseResponse$RequestStatus");
                Field success = purchaseResponseRequestStatusClass.getDeclaredField("SUCCESSFUL");

                if (requestStatus.equals(success.get(null))) {
                    m = purchaseResponseClass.getMethod("toJSON");
                    JSONObject json = (JSONObject) m.invoke(args[0]);
                    Teak.purchaseSucceeded(json);
                } else {
                    Teak.purchaseFailed(-1, null);
                }

                // onProductDataResponse()
            } else if (method.getName().equals("onProductDataResponse")) {
                Class<?> productDataResponseClass = Class.forName("com.amazon.device.iap.model.ProductDataResponse");
                Method m = productDataResponseClass.getMethod("getRequestStatus");
                Object requestStatus = m.invoke(args[0]);
                m = productDataResponseClass.getMethod("getRequestId");
                Object requestId = m.invoke(args[0]);

                Class<?> productDataResponseRequestStatusClass = Class.forName("com.amazon.device.iap.model.ProductDataResponse$RequestStatus");
                Field success = productDataResponseRequestStatusClass.getDeclaredField("SUCCESSFUL");

                if (requestStatus.equals(success.get(null))) {
                    m = productDataResponseClass.getMethod("getProductData");
                    @SuppressWarnings("unchecked") Map<String, Object> skuMap = (Map<String, Object>) m.invoke(args[0]);
                    Class<?> productClass = Class.forName("com.amazon.device.iap.model.Product");
                    m = productClass.getMethod("getPrice");

                    if (Teak.isDebug) {
                        Log.d(Teak.LOG_TAG, "SKU Details:");
                    }

                    for (Map.Entry<String, Object> entry : skuMap.entrySet()) {
                        String price = m.invoke(entry.getValue()).toString();
                        skuPriceMap.put(entry.getKey(), price);
                        if (Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "   " + entry.getKey() + " = " + price);
                        }
                    }

                    Object lock = skuDetailsRequestMap.get(requestId.toString());
                    synchronized (lock) {
                        lock.notify();
                    }
                } else {
                    if (Teak.isDebug) {
                        Log.d(Teak.LOG_TAG, "SKU detail query failed.");
                    }
                }
            } else {
                Log.d(Teak.LOG_TAG, "PurchasingListenerInvocationHandler");
                Log.d(Teak.LOG_TAG, "   method: " + method.getName());
                Log.d(Teak.LOG_TAG, "     args: " + args.toString());
            }

            return null;
        }
    }
}
