package io.teak.sdk.store;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.RequestId;
import com.amazon.device.iap.model.UserData;
import com.amazon.device.iap.model.UserDataResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import androidx.annotation.NonNull;
import io.teak.sdk.Helpers.mm;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.LifecycleEvent;
import io.teak.sdk.event.PurchaseEvent;
import io.teak.sdk.event.PurchaseFailedEvent;
import io.teak.sdk.json.JSONObject;

public class Amazon implements IStore {
    private HashMap<RequestId, ArrayBlockingQueue<String>> skuDetailsRequestMap;

    public void init(Context context) {
        this.skuDetailsRequestMap = new HashMap<>();
        try {
            PurchasingService.registerListener(context, new TeakPurchasingListener());

            Teak.log.i("amazon.iap", "Amazon In-App Purchasing 2.0 registered.", mm.h("sandboxMode", PurchasingService.IS_SANDBOX_MODE));

            TeakEvent.addEventListener(new TeakEvent.EventListener() {
                @Override
                public void onNewEvent(@NonNull TeakEvent event) {
                    if (event.eventType.equals(LifecycleEvent.Resumed)) {
                        PurchasingService.getUserData();
                    }
                }
            });
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    public void dispose() {
        // None
    }

    private JSONObject querySkuDetails(String sku) {
        HashSet<String> skus = new HashSet<>();
        skus.add(sku);
        RequestId requestId = PurchasingService.getProductData(skus);
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(1);

        skuDetailsRequestMap.put(requestId, queue);
        try {
            JSONObject ret = new JSONObject();
            ret.put("price_string", queue.take());
            skuDetailsRequestMap.remove(requestId);
            return ret;
        } catch (Exception e) {
            Teak.log.exception(e);
            return null;
        }
    }

    public void checkActivityResultForPurchase(int resultCode, Intent data) {
        // None
    }

    @Override
    public void processPurchase(String purchaseDataAsString, Map<String, Object> extras) {
        try {
            JSONObject originalJson = new JSONObject(purchaseDataAsString);
            this.processPurchaseJson(originalJson, extras);
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }

    private void processPurchaseJson(JSONObject originalJson, Map<String, Object> extras) {
        try {
            JSONObject receipt = originalJson.getJSONObject("receipt");
            JSONObject userData = originalJson.getJSONObject("userData");

            Map<String, Object> payload = (extras == null ? new HashMap<String, Object>() : extras);
            payload.put("purchase_token", receipt.get("receiptId"));
            payload.put("purchase_time_string", receipt.get("purchaseDate"));
            payload.put("product_id", receipt.get("sku"));
            payload.put("store_marketplace", userData.get("marketplace"));

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

    private class TeakPurchasingListener implements PurchasingListener {
        @Override
        public void onUserDataResponse(UserDataResponse userDataResponse) {
            if (userDataResponse.getRequestStatus() == UserDataResponse.RequestStatus.SUCCESSFUL) {
                UserData userData = userDataResponse.getUserData();

                String storeUserId = userData.getUserId();
                String storeMarketplace = userData.getMarketplace();
                // TODO: Do we want these for anything?
                Teak.log.i("amazon.iap.user", "Amazon Store User Details retrieved.", mm.h("storeUserId", storeUserId, "storeMarketplace", storeMarketplace));
            }
        }

        @Override
        public void onProductDataResponse(ProductDataResponse productDataResponse) {
            RequestId requestId = productDataResponse.getRequestId();
            ArrayBlockingQueue<String> queue = skuDetailsRequestMap.get(requestId);

            if (productDataResponse.getRequestStatus() == ProductDataResponse.RequestStatus.SUCCESSFUL && queue != null) {
                Map<String, Product> skuMap = productDataResponse.getProductData();

                for (Map.Entry<String, Product> entry : skuMap.entrySet()) {
                    String price = entry.getValue().getPrice();
                    Teak.log.i("amazon.iap.sku", "SKU Details retrieved.", mm.h(entry.getKey(), price));
                    queue.offer(price);
                }
            } else {
                Teak.log.e("amazon.iap.sku", "SKU Details query failed.");
            }
        }

        @Override
        public void onPurchaseResponse(PurchaseResponse purchaseResponse) {
            if (purchaseResponse.getRequestStatus() == PurchaseResponse.RequestStatus.SUCCESSFUL) {
                try {
                    processPurchase(purchaseResponse.toJSON().toString(), null);
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            } else {
                TeakEvent.postEvent(new PurchaseFailedEvent(-1, null));
            }
        }

        @Override
        public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse purchaseUpdatesResponse) {
            // None
        }
    }
}
