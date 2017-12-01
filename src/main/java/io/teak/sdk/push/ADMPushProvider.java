/* Teak -- Copyright (C) 2017 GoCarrot Inc.
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
package io.teak.sdk.push;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.support.annotation.NonNull;
import android.util.Base64;

import android.content.Intent;

import com.amazon.device.messaging.ADM;
import com.amazon.device.messaging.ADMMessageHandlerBase;

import org.json.teak.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.teak.sdk.Helpers;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.PushRegistrationEvent;

public class ADMPushProvider extends ADMMessageHandlerBase implements IPushProvider {
    private ADM admInstance;

    public ADMPushProvider() {
        super(ADMPushProvider.class.getName());
    }

    public void initialize(@NonNull Context context) {
        this.admInstance = new ADM(context);
    }

    ///// IPushProvider

    @Override
    public void requestPushKey(@NonNull String ignored) {
        if (this.admInstance.getRegistrationId() == null) {
            this.admInstance.startRegister();
        } else {
            String registrationId = this.admInstance.getRegistrationId();
            sendRegistrationEvent(registrationId);
        }
    }

    ///// Helper

    private void sendRegistrationEvent(@NonNull String registrationId) {
        Teak.log.i("amazon.adm.registered", Helpers.mm.h("admId", registrationId));
        if (Teak.isEnabled()) {
            TeakEvent.postEvent(new PushRegistrationEvent("adm_push_key", registrationId));
        }
    }

    ///// ADMMessageHandlerBase

    @Override
    protected void onMessage(Intent intent) {
        if (Teak.isEnabled()) {
            TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Received, getApplicationContext(), intent));
        }
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
        Teak.log.e("amazon.adm.registration_error", "Error registering for ADM id: " + s);

        // If the error is INVALID_SENDER try and help the developer
        if (s.contains("INVALID_SENDER")) {

            // First check to see if api_key.txt is available
            InputStream inputStream = null;
            try {
                inputStream = getApplicationContext().getAssets().open("api_key.txt");
            } catch (IOException e) {
                Teak.log.e("amazon.adm.registration_error", "Unable to find 'api_key.txt' in assets, this is required for debugging. Please see: https://developer.amazon.com/public/apis/engage/device-messaging/tech-docs/04-integrating-your-app-with-adm");
            }

            // Check for whitespace
            if (inputStream != null) {
                Teak.log.i("amazon.adm.registration_error.debugging", "[✓] 'api_key.txt' found in assets");
                try {
                    Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
                    String keyText = scanner.hasNext() ? scanner.next() : "";

                    Pattern pattern = Pattern.compile("\\s");
                    Matcher matcher = pattern.matcher(keyText);
                    if (matcher.find()) {
                        throw new Exception("Whitespace found in 'api_key.txt'");
                    }

                    // Ok, no whitespace, so decode the key
                    Teak.log.i("amazon.adm.registration_error.debugging", "[✓] No whitespace inside 'api_key.txt'");
                    String[] keySections = keyText.split("\\.");
                    if (keySections.length != 3) {
                        throw new Exception("Potentially malformed contents of 'api_key.txt', does not contain three sections delimited by '.'");
                    }
                    Teak.log.i("amazon.adm.registration_error.debugging", "[✓] Found validation section inside 'api_key.txt'");
                    String middleJson = new String(Base64.decode(keySections[1], Base64.DEFAULT), "UTF-8");
                    JSONObject json = new JSONObject(middleJson);

                    // Make sure the key and the package name match
                    String packageName = getApplicationContext().getPackageName();
                    if (!packageName.equals(json.getString("pkg"))) {
                        Teak.log.e("amazon.adm.registration_error.debugging", Helpers.mm.h("packageName", packageName, "api_key.packageName", json.getString("pkg")));
                        throw new Exception("Package name mismatch in 'api_key.txt'");
                    }
                    Teak.log.i("amazon.adm.registration_error.debugging", "[✓] App package name matches package name inside 'api_key.txt'");

                    // Make sure the signature matches
                    @SuppressLint("PackageManagerGetSignatures")
                    Signature[] sigs = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), PackageManager.GET_SIGNATURES).signatures;
                    for (Signature sig : sigs) {
                        if (json.has("appsigSha256")) {
                            String sigSha256 = formatSig(sig, "SHA-256");
                            if (!sigSha256.equalsIgnoreCase(json.getString("appsigSha256"))) {
                                Teak.log.e("amazon.adm.registration_error.debugging", Helpers.mm.h("sha256", sigSha256, "api_key.sha256", json.getString("appsigSha256")));
                                throw new Exception("App signature SHA-256 does not match api_key.txt");
                            }
                            Teak.log.i("amazon.adm.registration_error.debugging", "[✓] App signature matches signature inside 'api_key.txt'");
                        } else if (json.has("appsig")) {
                            String sigMd5 = formatSig(sig, "MD5");
                            if (!sigMd5.equalsIgnoreCase(json.getString("appsig"))) {
                                Teak.log.e("amazon.adm.registration_error.debugging", Helpers.mm.h("md5", sigMd5, "api_key.md5", json.getString("appsig")));
                                throw new Exception("App signature MD5 does not match api_key.txt");
                            }
                        } else {
                            String sigMd5 = formatSig(sig, "MD5");
                            String sigSha256 = formatSig(sig, "SHA-256");
                            Teak.log.w("amazon.adm.registration_error.debugging", "Couldn't find 'appsigSha256' or 'appsig' please ensure that your API key matches one of the included signatures.",
                                Helpers.mm.h("md5", sigMd5, "sha256", sigSha256));
                        }
                    }

                    // Couldn't find the error, sorry!
                    Teak.log.w("amazon.adm.registration_error.debugging", "Unable to automatically find reason for INVALID_SENDER");
                } catch (Exception e) {
                    Teak.log.exception(e);
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
        sendRegistrationEvent(s);
    }

    @Override
    protected void onUnregistered(String s) {
        Teak.log.i("amazon.adm.unregistered", Helpers.mm.h("admId", s));
        if (Teak.isEnabled()) {
            TeakEvent.postEvent(new PushRegistrationEvent(PushRegistrationEvent.UnRegistered));
        }
    }
}
