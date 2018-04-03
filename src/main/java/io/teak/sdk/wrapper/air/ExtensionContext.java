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

import java.util.Map;
import java.util.HashMap;

import android.support.annotation.NonNull;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;

import io.teak.sdk.wrapper.ISDKWrapper;
import io.teak.sdk.wrapper.TeakInterface;

public class ExtensionContext extends FREContext {
    @SuppressWarnings("FieldCanBeLocal")
    private final TeakInterface teakInterface;
    private final String initializationErrors;

    @SuppressWarnings("WeakerAccess")
    public ExtensionContext() {
        TeakInterface tempTeakInterface = null;
        String tempInitializationErrors = null;
        try {
            tempTeakInterface = new TeakInterface(new ISDKWrapper() {
                @Override
                public void sdkSendMessage(@NonNull EventType eventType, @NonNull String eventData) {
                    String eventName = null;
                    switch (eventType) {
                        case NotificationLaunch: {
                            eventName = "LAUNCHED_FROM_NOTIFICATION";
                        } break;
                        case RewardClaim: {
                            eventName = "ON_REWARD";
                        } break;
                    }
                    Extension.context.dispatchStatusEventAsync(eventName, eventData);
                }
            });
        } catch (IllegalStateException e) {
            tempInitializationErrors = e.getLocalizedMessage();
        } finally {
            this.teakInterface = tempTeakInterface;
            this.initializationErrors = tempInitializationErrors;
        }

        // TODO: Should this be delayed?
        if (this.teakInterface != null) {
            this.teakInterface.readyForDeepLinks();
        }
    }

    @Override
    public Map<String, FREFunction> getFunctions() {
        Map<String, FREFunction> functionMap = new HashMap<>();
        functionMap.put("identifyUser", new IdentifyUserFunction());
        functionMap.put("_log", new LogFunction());
        functionMap.put("scheduleNotification", new TeakNotificationFunction(TeakNotificationFunction.CallType.Schedule));
        functionMap.put("cancelNotification", new TeakNotificationFunction(TeakNotificationFunction.CallType.Cancel));
        functionMap.put("cancelAllNotifications", new TeakNotificationFunction(TeakNotificationFunction.CallType.CancelAll));
        functionMap.put("registerRoute", new RegisterRouteFunction());
        functionMap.put("getVersion", new GetVersionFunction());
        functionMap.put("getInitializationErrors", new GetInitializationErrorsFunction(this.initializationErrors));
        functionMap.put("setNumericAttribute", new SetAttributeFunction(SetAttributeFunction.FunctionType.Numeric));
        functionMap.put("setStringAttribute", new SetAttributeFunction(SetAttributeFunction.FunctionType.String));
        functionMap.put("openSettingsAppToThisAppsSettings", new OpenSettingsAppToThisAppsSettingsFunction());
        functionMap.put("areNotificationsEnabled", new AreNotificationsEnabledFunction());
        return functionMap;
    }

    @Override
    public void dispose() {
        // None
    }
}
