package io.teak.sdk.store;

import android.content.Context;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.PurchaseEvent;
import io.teak.sdk.json.JSONObject;

public class GooglePlayBillingV3 implements IStore, PurchasesUpdatedListener {
    private final BillingClient billingClient;

    public GooglePlayBillingV3(Context context) {
        this.billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build();
        Teak.log.i("billing.google.v3", "Google Play Billing v3 registered.");
    }

    @Override
    public void processPurchase(String purchaseDataAsString, Map<String, Object> extras) {
        final JSONObject purchaseData = new JSONObject(purchaseDataAsString);
        final String sku = (String) purchaseData.get("productId");
        final Map<String, Object> payload = (extras == null ? new HashMap<>() : extras);
        payload.put("purchase_token", purchaseData.get("purchaseToken"));
        payload.put("purchase_time", purchaseData.get("purchaseTime"));
        payload.put("product_id", sku);
        if (purchaseData.has("orderId")) {
            payload.put("order_id", purchaseData.get("orderId"));
        }

        final SkuDetailsParams params = SkuDetailsParams
                .newBuilder()
                .setSkusList(Collections.singletonList(sku))
                .build();
        this.billingClient.querySkuDetailsAsync(params, (billingResult, list) -> {
            if (list != null && !list.isEmpty()) {
                final SkuDetails skuDetails = list.get(0);
                payload.put("price_amount_micros", skuDetails.getPriceAmountMicros());
                payload.put("price_currency_code", skuDetails.getPriceCurrencyCode());
            }

            TeakEvent.postEvent(new PurchaseEvent(payload));
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
        Teak.log.i("billing.google.v3", "onPurchasesUpdated" + billingResult.toString());
    }
}
