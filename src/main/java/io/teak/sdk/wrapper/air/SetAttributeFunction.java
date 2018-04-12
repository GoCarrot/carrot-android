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

import io.teak.sdk.Teak;

public class SetAttributeFunction implements FREFunction {
    public enum FunctionType {
        Numeric,
        String
    }

    private final FunctionType functionType;

    SetAttributeFunction(FunctionType functionType) {
        this.functionType = functionType;
    }

    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            switch (this.functionType) {
                case Numeric: {
                    Teak.setNumericAttribute(argv[0].getAsString(), argv[1].getAsDouble());
                } break;

                case String: {
                    Teak.setStringAttribute(argv[0].getAsString(), argv[1].getAsString());
                } break;
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        return null;
    }
}
