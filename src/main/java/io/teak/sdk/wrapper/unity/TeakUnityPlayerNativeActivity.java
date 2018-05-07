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
package io.teak.sdk.wrapper.unity;

import android.util.Log;

import android.os.Bundle;

import io.teak.sdk.Unobfuscable;

public class TeakUnityPlayerNativeActivity extends TeakUnityPlayerActivity implements Unobfuscable {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e("Teak", "TeakUnityPlayerNativeActivity has been deprecated, please update your AndroidManifest to use TeakUnityPlayerActivity instead");
        super.onCreate(savedInstanceState);
    }
}
