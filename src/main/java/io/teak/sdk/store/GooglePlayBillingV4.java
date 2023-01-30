package io.teak.sdk.store;

import android.content.Context;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.Helpers;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.Unobfuscable;
import io.teak.sdk.event.PurchaseEvent;

@SuppressWarnings("deprecation")
public class GooglePlayBillingV4 implements Unobfuscable, IStore, PurchasesUpdatedListener, BillingClientStateListener {
    private final BillingClient billingClient;

    public GooglePlayBillingV4(Context context) {
        this.billingClient = BillingClient.newBuilder(context)
                                 .setListener(this)
                                 .enablePendingPurchases()
                                 .build();
        Teak.log.i("billing.google.v4", "Google Play Billing v4 registered.");

        this.billingClient.startConnection(this);
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
    }

    @Override
    public void onBillingServiceDisconnected() {
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchaseList) {
        if (purchaseList == null) {
            return;
        }

        try {
            for (Purchase purchase : purchaseList) {
                final ArrayList<String> skusInPurchase = purchase.getSkus();
                for (String purchaseSku : skusInPurchase) {
                    final Map<String, Object> payload = new HashMap<>();
                    payload.put("purchase_token", purchase.getPurchaseToken());
                    payload.put("purchase_time", purchase.getPurchaseTime());
                    payload.put("product_id", purchaseSku);
                    payload.put("order_id", purchase.getOrderId());

                    final com.android.billingclient.api.SkuDetailsParams params = com.android.billingclient.api.SkuDetailsParams
                                                                                      .newBuilder()
                                                                                      .setType(BillingClient.SkuType.INAPP)
                                                                                      .setSkusList(Collections.singletonList(purchaseSku))
                                                                                      .build();

                    this.billingClient.querySkuDetailsAsync(params, (ignored, skuDetailsList) -> {
                        try {
                            if (skuDetailsList != null && !skuDetailsList.isEmpty()) {
                                final com.android.billingclient.api.SkuDetails skuDetails = skuDetailsList.get(0);
                                payload.put("price_amount_micros", skuDetails.getPriceAmountMicros());
                                payload.put("price_currency_code", skuDetails.getPriceCurrencyCode());

                                Teak.log.i("billing.google.v4.sku", "SKU Details retrieved.", Helpers.mm.h(purchaseSku, skuDetails.getPriceAmountMicros()));
                            } else {
                                Teak.log.e("billing.google.v4.sku", "SKU Details query failed.");
                            }

                            TeakEvent.postEvent(new PurchaseEvent(payload));
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }
}
