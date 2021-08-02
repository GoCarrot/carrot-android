package io.teak.sdk.push;

import android.content.Context;
import android.content.Intent;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Base64;

import com.amazon.device.messaging.ADM;
import com.amazon.device.messaging.ADMMessageHandlerBase;
import com.amazon.device.messaging.ADMMessageHandlerJobBase;
import com.amazon.device.messaging.ADMMessageReceiver;
import com.amazon.device.messaging.development.ADMManifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.teak.sdk.Helpers;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.Unobfuscable;
import io.teak.sdk.core.Executors;
import io.teak.sdk.core.TeakCore;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.PushRegistrationEvent;
import io.teak.sdk.json.JSONObject;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class ADMPushProvider implements IPushProvider, Unobfuscable {
    private ADM admInstance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void initialize(@NonNull Context context) {
        this.admInstance = new ADM(context);

        try {
            ADMManifest.checkManifestAuthoredProperly(context);
        } catch (NoClassDefFoundError ignored) {
            android.util.Log.e("Teak", "Add this to your <application> in AndroidManifest.xml in order to use ADM: <amazon:enable-feature android:name=\"com.amazon.device.messaging\" android:required=\"false\" />");
        }
    }

    ///// ADMMessageReceiver

    public static class MessageAlertReceiver extends ADMMessageReceiver implements Unobfuscable {
        static boolean ADM_1_1_0 = false;
        static {
            try {
                Class.forName("com.amazon.device.messaging.ADMMessageHandlerJobBase");
                ADM_1_1_0 = true;
            } catch (Exception ignored) {
            }
        }

        public MessageAlertReceiver() {
            super(ADMMessageHandler_1_0_1.class);

            if (ADM_1_1_0) {
                Teak.log.i("amazon.adm.jobservice", "Registering ADMMessageHandler_1_1_0 job service.");
                registerJobServiceClass(ADMMessageHandler_1_1_0.class, io.teak.sdk.Teak.JOB_ID);
            }
        }
    }

    ///// IPushProvider

    @Override
    public void requestPushKey() {
        executor.execute(() -> {
            if (admInstance.getRegistrationId() == null) {
                admInstance.startRegister();
            } else {
                String registrationId = admInstance.getRegistrationId();
                sendRegistrationEvent(registrationId);
            }
        });
    }

    ///// Helper

    private static void sendRegistrationEvent(@NonNull String registrationId) {
        Teak.log.i("amazon.adm.registered", Helpers.mm.h("admId", registrationId));
        if (Teak.isEnabled()) {
            TeakEvent.postEvent(new PushRegistrationEvent("adm_push_key", registrationId, null));
        }
    }

    ///// Functionality

    private static void onMessage(Context context, Intent intent) {
        final TeakCore teakCore = TeakCore.getWithoutThrow(context);
        if (teakCore == null) {
            Teak.log.e("amazon.adm.null_teak_core", "TeakCore.getWithoutThrow returned null.");
        }

        // "external" is used to chain FCM receivers and so ADM output should match, even though
        // we do not support chaining ADM receivers.
        Teak.log.i("amazon.adm.received", Helpers.mm.h("external", false));

        if (intent.hasExtra("teakAdm")) {
            JSONObject teakAdm = new JSONObject(intent.getStringExtra("teakAdm"));
            intent.putExtras(Helpers.jsonToGCMBundle(teakAdm));
            TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Received, context, intent));
        }
    }

    private static void onRegistrationError(Context context, String s) {
        Teak.log.e("amazon.adm.registration_error", "Error registering for ADM id: " + s);

        // If the error is INVALID_SENDER try and help the developer
        if (s != null && s.contains("INVALID_SENDER")) {

            // First check to see if api_key.txt is available
            InputStream inputStream = null;
            try {
                inputStream = context.getAssets().open("api_key.txt");
            } catch (IOException e) {
                Teak.log.e("amazon.adm.registration_error", "Unable to find 'api_key.txt' in assets, this is required for ADM use. Please see: https://developer.amazon.com/docs/adm/integrate-your-app.html#store-your-api-key-as-an-asset");
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
                    String middleJson = new String(Base64.decode(keySections[1], Base64.DEFAULT), StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(middleJson);

                    // Make sure the key and the package name match
                    String packageName = context.getPackageName();
                    if (!packageName.equals(json.getString("pkg"))) {
                        Teak.log.e("amazon.adm.registration_error.debugging", Helpers.mm.h("packageName", packageName, "api_key.packageName", json.getString("pkg")));
                        throw new Exception("Package name mismatch in 'api_key.txt'");
                    }
                    Teak.log.i("amazon.adm.registration_error.debugging", "[✓] App package name matches package name inside 'api_key.txt'");

                    // Make sure the signature matches
                    Signature[] sigs = Helpers.getAppSignatures(context);
                    if (sigs != null) {
                        for (Signature sig : sigs) {
                            if (json.has("appsigSha256")) {
                                String sigSha256 = Helpers.formatSig(sig, "SHA-256");
                                if (!sigSha256.equalsIgnoreCase(json.getString("appsigSha256"))) {
                                    Teak.log.e("amazon.adm.registration_error.debugging", Helpers.mm.h("sha256", sigSha256, "api_key.sha256", json.getString("appsigSha256")));
                                    throw new Exception("App signature SHA-256 does not match api_key.txt");
                                }
                            }

                            if (json.has("appsig")) {
                                String sigMd5 = Helpers.formatSig(sig, "MD5");
                                if (!sigMd5.equalsIgnoreCase(json.getString("appsig"))) {
                                    Teak.log.e("amazon.adm.registration_error.debugging", Helpers.mm.h("md5", sigMd5, "api_key.md5", json.getString("appsig")));
                                    throw new Exception("App signature MD5 does not match api_key.txt");
                                }
                            }

                            if (!json.has("appsigSha256") || !json.has("appsig")) {
                                String sigMd5 = Helpers.formatSig(sig, "MD5");
                                String sigSha256 = Helpers.formatSig(sig, "SHA-256");
                                Teak.log.w("amazon.adm.registration_error.debugging", "Couldn't find 'appsigSha256' or 'appsig' please ensure that your API key matches one of the included signatures.",
                                        Helpers.mm.h("md5", sigMd5, "sha256", sigSha256));
                            }
                        }
                        Teak.log.i("amazon.adm.registration_error.debugging", "[✓] App signature matches signature inside 'api_key.txt'");
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

    private static void onUnregistered(String s) {
        Teak.log.i("amazon.adm.unregistered", Helpers.mm.h("admId", s));
        if (Teak.isEnabled()) {
            TeakEvent.postEvent(new PushRegistrationEvent(PushRegistrationEvent.UnRegistered));
        }
    }

    ///// ADMMessageHandlerBase (1.0.1)

    public static class ADMMessageHandler_1_0_1 extends ADMMessageHandlerBase implements Unobfuscable {
        public ADMMessageHandler_1_0_1() {
            super(ADMMessageHandler_1_0_1.class.getName());
        }

        @Override
        protected void onMessage(Intent intent) {
            ADMPushProvider.onMessage(getApplicationContext(), intent);
        }

        @Override
        protected void onRegistrationError(String s) {
            ADMPushProvider.onRegistrationError(getApplicationContext(), s);
        }

        @Override
        protected void onRegistered(String s) {
            ADMPushProvider.sendRegistrationEvent(s);
        }

        @Override
        protected void onUnregistered(String s) {
            ADMPushProvider.onUnregistered(s);
        }
    }

    ///// ADMMessageHandlerJobBase (1.1.0)

    public static class ADMMessageHandler_1_1_0 extends ADMMessageHandlerJobBase implements Unobfuscable {
        public ADMMessageHandler_1_1_0() {
            super();
        }

        @Override
        protected void onMessage(Context context, Intent intent) {
            ADMPushProvider.onMessage(context, intent);
        }

        @Override
        protected void onRegistrationError(Context context, String s) {
            ADMPushProvider.onRegistrationError(context, s);
        }

        @Override
        protected void onRegistered(Context context, String s) {
            ADMPushProvider.sendRegistrationEvent(s);
        }

        @Override
        protected void onUnregistered(Context context, String s) {
            ADMPushProvider.onUnregistered(s);
        }
    }
}
