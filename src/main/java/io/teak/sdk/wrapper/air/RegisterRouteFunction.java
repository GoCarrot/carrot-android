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

import org.json.JSONObject;

import java.util.Map;

import io.teak.sdk.Teak;

public class RegisterRouteFunction implements FREFunction {
    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            final String route = argv[0].getAsString();
            final String name = argv[1].getAsString();
            final String description = argv[2].getAsString();

            Teak.registerDeepLink(route, name, description, new Teak.DeepLink() {
                @Override
                public void call(Map<String, Object> parameters) {
                    try {
                        JSONObject eventData = new JSONObject();
                        eventData.put("route", route);
                        eventData.put("parameters", new JSONObject(parameters));
                        Extension.context.dispatchStatusEventAsync("DEEP_LINK", eventData.toString());
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }
                }
            });
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        return null;
    }
}
