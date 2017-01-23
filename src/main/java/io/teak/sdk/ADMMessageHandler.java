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

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;
import android.util.Log;

import android.content.Intent;

import com.amazon.device.messaging.ADMMessageHandlerBase;
import com.amazon.device.messaging.ADMMessageReceiver;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ADMMessageHandler extends ADMMessageHandlerBase {
    private static final String LOG_TAG = "Teak:ADMMessageHandler";

    @Override
    protected void onMessage(Intent intent) {
        if (!Teak.isEnabled()) {
            Log.e(LOG_TAG, "Teak is disabled, ignoring onMessage().");
            return;
        }

        Teak.handlePushNotificationReceived(getApplicationContext(), intent);
    }

    private static String formatSig(Signature sig, String hashType) throws java.security.NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(hashType);
        byte[] sha256Bytes = md.digest(sig.toByteArray());
        StringBuilder hexString = new StringBuilder();
        for (byte aSha256Byte : sha256Bytes) {
            if (hexString.length() > 0) {
                hexString.append(":");
            }

            if ((0xff & aSha256Byte) < 0x10) {
                hexString.append("0").append(Integer.toHexString((0xFF & aSha256Byte)));
            } else {
                hexString.append(Integer.toHexString(0xFF & aSha256Byte));
            }
        }
        return hexString.toString().toUpperCase();
    }

    @Override
    protected void onRegistrationError(String s) {
        Log.e(LOG_TAG, "Error registering for ADM id: " + s);

        // If the error is INVALID_SENDER try and help the developer
        if (Teak.isDebug && s.contains("INVALID_SENDER")) {

            // First check to see if api_key.txt is available
            Log.i(LOG_TAG, "Attempting to determine cause of INVALID_SENDER error...");
            InputStream inputStream = null;
            try {
                inputStream = getApplicationContext().getAssets().open("api_key.txt");
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to find 'api_key.txt' in assets, this is required for debugging. Please see: https://developer.amazon.com/public/apis/engage/device-messaging/tech-docs/04-integrating-your-app-with-adm");
            }

            // Check for whitespace
            if (inputStream != null) {
                Log.i(LOG_TAG, "[✓] 'api_key.txt' found in assets");
                try {
                    Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
                    String keyText = scanner.hasNext() ? scanner.next() : "";

                    Pattern pattern = Pattern.compile("\\s");
                    Matcher matcher = pattern.matcher(keyText);
                    if (matcher.find()) {
                        throw new Exception("Whitespace found in 'api_key.txt'");
                    }

                    // Ok, no whitespace, so decode the key
                    Log.i(LOG_TAG, "[✓] No whitespace inside 'api_key.txt'");
                    String[] keySections = keyText.split("\\.");
                    if (keySections.length != 3) {
                        throw new Exception("Potentially malformed contents of 'api_key.txt', does not contain three sections delimited by '.'");
                    }
                    Log.i(LOG_TAG, "[✓] Found validation section inside 'api_key.txt'");
                    String middleJson = new String(Base64.decode(keySections[1], Base64.DEFAULT), "UTF-8");
                    JSONObject json = new JSONObject(middleJson);

                    // Make sure the key and the package name match
                    String packageName = getApplicationContext().getPackageName();
                    if (!packageName.equals(json.getString("pkg"))) {
                        Log.e(LOG_TAG, "               Package name: " + packageName);
                        Log.e(LOG_TAG, "Package name in api_key.txt: " + json.getString("pkg"));
                        throw new Exception("Package name mismatch in 'api_key.txt'");
                    }
                    Log.i(LOG_TAG, "[✓] App package name matches package name inside 'api_key.txt'");

                    // Make sure the signature matches
                    @SuppressLint("PackageManagerGetSignatures") Signature[] sigs = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), PackageManager.GET_SIGNATURES).signatures;
                    for (Signature sig : sigs) {
                        if (json.has("appsigSha256")) {
                            String sigSha256 = formatSig(sig, "SHA-256");
                            if (!sigSha256.equalsIgnoreCase(json.getString("appsigSha256"))) {
                                Log.e(LOG_TAG, "           App SHA-256:" + sigSha256);
                                Log.e(LOG_TAG, "   api_key.txt SHA-256:" + json.getString("appsigSha256"));
                                throw new Exception("App signature SHA-256 does not match api_key.txt");
                            }
                            Log.i(LOG_TAG, "[✓] App signature matches signature inside 'api_key.txt'");
                        } else {
                            // TODO: What would old version contain? 'appsig' == md5?
                            String sigMd5 = formatSig(sig, "MD5");
                            String sigSha256 = formatSig(sig, "SHA-256");
                            Log.w(LOG_TAG, "Couldn't find 'appsigSha256' please ensure that your API key matches one of the following signatures:");
                            Log.w(LOG_TAG, "       MD5:" + sigMd5);
                            Log.w(LOG_TAG, "   SHA-256:" + sigSha256);
                        }
                    }

                    // Couldn't find the error, sorry!
                    Log.w(LOG_TAG, "Unable to automatically find reason for INVALID_SENDER");
                } catch (Exception e) {
                    Log.e(LOG_TAG, e.getMessage());
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    @Override
    protected void onRegistered(String s) {
        Teak.deviceConfiguration.admId = s;
        Teak.deviceConfiguration.notifyPushIdChangedListeners();
    }

    @Override
    protected void onUnregistered(String s) {
        Teak.deviceConfiguration.admId = null;
        Teak.deviceConfiguration.notifyPushIdChangedListeners();
    }

    public static class MessageAlertReceiver extends ADMMessageReceiver {
        public MessageAlertReceiver() {
            super(ADMMessageHandler.class);
        }
    }

    public ADMMessageHandler() {
        super(ADMMessageHandler.class.getName());
    }
}
