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

import java.util.Arrays;

import io.teak.sdk.Teak;
import io.teak.sdk.json.JSONArray;

public class IdentifyUserFunction implements FREFunction {
    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        String[] optOut = new String[]{};
        try {
            final JSONArray jsonList = new JSONArray(argv[1].getAsString());
            optOut = Arrays.copyOf(jsonList.toList().toArray(), jsonList.length(), String[].class);
        } catch (Exception e) {
            Teak.log.exception(e);
        }

        try {
            Teak.identifyUser(argv[0].getAsString(), optOut);
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        return null;
    }
}
