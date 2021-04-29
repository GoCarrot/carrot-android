package io.teak.sdk.store;

import java.util.Map;

public interface IStore {
    void processPurchase(String purchaseDataAsString, Map<String, Object> extras);
}
