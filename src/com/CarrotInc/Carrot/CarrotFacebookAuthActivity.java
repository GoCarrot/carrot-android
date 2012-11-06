/* Carrot -- Copyright (C) 2012 Carrot Inc.
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
package com.CarrotInc.Carrot;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.facebook.android.carrot.*;

public class CarrotFacebookAuthActivity extends Activity {
   Carrot mCarrot;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      mCarrot = new Carrot(this, getIntent().getStringExtra("appId"),
         getIntent().getStringExtra("appSecret"));

      mCarrot.setHandler(new Carrot.Handler() {
         @Override
         public void authenticationStatusChanged(int authStatus) {
            Log.d("Carrot", "Auth status changed: " + authStatus);
            finish();
         }
      });

      mCarrot.getFacebook().authorize(this, new String[] {"publish_actions"},
         new Facebook.DialogListener() {
            @Override
            public void onComplete(Bundle values) {
               mCarrot.setAccessToken(mCarrot.getFacebook().getAccessToken());
            }

            @Override
            public void onFacebookError(FacebookError error) {}

            @Override
            public void onError(DialogError e) {}

            @Override
            public void onCancel() {}
         }
      );
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      super.onActivityResult(requestCode, resultCode, data);
      mCarrot.authorizeCallback(requestCode, resultCode, data);
   }
}
