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

import android.content.Context;

import android.os.Build;
import android.text.TextUtils;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

class Helpers {
    static String getStringResourceByName(String name, Context context) {
        try {
            String packageName = context.getPackageName();
            int resId = context.getResources().getIdentifier(name, "string", packageName);
            return context.getString(resId);
        } catch (Exception ignored) {
        }
        return null;
    }

    static Map<String, Object> jsonToMap(JSONObject json) throws JSONException {
        Map<String, Object> retMap = new HashMap<>();

        if (json != JSONObject.NULL) {
            retMap = toMap(json);
        }
        return retMap;
    }

    private static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new HashMap<>();

        Iterator keysItr = object.keys();
        while (keysItr.hasNext()) {
            String key = keysItr.next().toString();
            Object value = object.get(key);

            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    private static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    // From:
    // https://raw.githubusercontent.com/jaredrummler/AndroidDeviceNames/master/library/src/main/java/com/jaredrummler/android/device/DeviceName.java
    static void addDeviceNameToPayload(Map<String, Object> payload) {
        payload.put("device_manufacturer", Build.MANUFACTURER == null ? "" : Build.MANUFACTURER);
        payload.put("device_model", Build.MODEL == null ? "" : Build.MODEL);
        if (Build.MODEL.startsWith(Build.MANUFACTURER)) {
            payload.put("device_fallback", capitalize(Build.MODEL));
        } else {
            payload.put("device_fallback", capitalize(Build.MANUFACTURER) + " " + Build.MODEL);
        }
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;
        String phrase = "";
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase += Character.toUpperCase(c);
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase += c;
        }
        return phrase;
    }
}
