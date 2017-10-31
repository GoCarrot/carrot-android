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
package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

import java.util.concurrent.Future;

import io.teak.sdk.Teak;
import io.teak.sdk.TeakNotification;

public class TeakNotificationFunction implements FREFunction {
    public enum CallType {
        Schedule("NOTIFICATION_SCHEDULED"),
        Cancel("NOTIFICATION_CANCELED"),
        CancelAll("NOTIFICATION_CANCEL_ALL");

        private final String text;

        @Override
        public String toString() {
            return text;
        }

        private CallType(final String text) {
            this.text = text;
        }
    }
    ;

    private final CallType callType;

    public TeakNotificationFunction(CallType callType) {
        this.callType = callType;
    }

    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            final Future<String> future = callType == CallType.Cancel ? TeakNotification.cancelNotification(argv[0].getAsString()) : (callType == CallType.CancelAll ? TeakNotification.cancelAll() : TeakNotification.scheduleNotification(argv[0].getAsString(), argv[1].getAsString(), (long) argv[2].getAsDouble()));
            if (future != null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String json = future.get();
                            Extension.context.dispatchStatusEventAsync(callType.toString(), json);
                        } catch (Exception e) {
                            Teak.log.exception(e);
                        }
                    }
                })
                    .start();
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        return null;
    }
}
