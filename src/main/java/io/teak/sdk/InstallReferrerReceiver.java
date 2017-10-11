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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.util.concurrent.ConcurrentLinkedQueue;

import io.teak.sdk.Helpers.mm;

public class InstallReferrerReceiver extends BroadcastReceiver {

    public static final ConcurrentLinkedQueue<String> installReferrerQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        String installReferrer = intent.getStringExtra("referrer");
        if (installReferrer != null && !installReferrer.isEmpty()) {
            Teak.log.i("install_referrer", mm.h("referrer", installReferrer));
            installReferrerQueue.offer(installReferrer);
        }

        try {
            ActivityInfo ai = context.getPackageManager().getReceiverInfo(new ComponentName(context, "io.teak.sdk.InstallReferrerReceiver"), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            for (String key : bundle.keySet()) {
                try {
                    String receiverClassName = (String) bundle.get(key);
                    Class<?> clazz = Class.forName(receiverClassName);
                    BroadcastReceiver receiver = (BroadcastReceiver) clazz.newInstance();
                    receiver.onReceive(context, intent);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
    }
}
