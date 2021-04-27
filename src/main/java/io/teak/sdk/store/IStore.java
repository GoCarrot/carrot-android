package io.teak.sdk.store;

import android.content.Context;
import android.content.Intent;
import java.util.Map;

public interface IStore {
    void init(Context context);

    void dispose();

    void processPurchase(String purchaseDataAsString, Map<String, Object> extras);

    void checkActivityResultForPurchase(int resultCode, Intent data);
}
