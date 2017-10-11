/* Teak -- Copyright (C) 2017 GoCarrot Inc.
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

package io.teak.app.test;

import android.content.Intent;
import android.support.test.runner.AndroidJUnit4;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import org.skyscreamer.jsonassert.JSONAssert;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class purchase_OSListenerIntegrationTests extends TeakIntegrationTests {

    @Test
    public void purchaseSucceeded_Prime31() throws JSONException {
        launchActivity();

        String json = "{}";

        call_prime31PurchaseSucceeded(json);

        ArgumentCaptor<JSONObject> argument = ArgumentCaptor.forClass(JSONObject.class);
        verify(osListener, timeout(3000).times(1)).purchase_onPurchaseSucceeded(argument.capture());

        JSONAssert.assertEquals(json, argument.getValue(), false);
    }

    @Test
    public void purchaseSucceeded_OpenIAB() throws JSONException {
        launchActivity();

        JSONObject json = new JSONObject();
        json.put("originalJson", new JSONObject());

        call_openIABPurchaseSucceeded(json.toString());

        ArgumentCaptor<JSONObject> argument = ArgumentCaptor.forClass(JSONObject.class);
        verify(osListener, timeout(3000).times(1)).purchase_onPurchaseSucceeded(argument.capture());

        JSONAssert.assertEquals(json.getJSONObject("originalJson"), argument.getValue(), false);
    }

    @Test
    public void purchaseFailed_plugin() throws JSONException {
        launchActivity();

        int errorCode = 42;
        JSONObject json = new JSONObject();
        json.put("errorCode", errorCode);

        call_pluginPurchaseFailed(errorCode);

        ArgumentCaptor<JSONObject> argument = ArgumentCaptor.forClass(JSONObject.class);
        verify(osListener, timeout(3000).times(1)).purchase_onPurchaseFailed(argument.capture());
        JSONAssert.assertEquals(json, argument.getValue(), false);
    }

    @Test
    public void purchase_onActivityResult() {
        launchActivity();

        int resultCode = 42;
        Intent data = new Intent();

        call_onActivityResult(resultCode, data);

        verify(iStore, timeout(3000).times(1)).checkActivityResultForPurchase(resultCode, data);
    }
}
