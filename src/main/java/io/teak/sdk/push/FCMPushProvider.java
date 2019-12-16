package io.teak.sdk.push;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import io.teak.sdk.Helpers;
import io.teak.sdk.IntegrationChecker;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakEvent;
import io.teak.sdk.core.TeakCore;
import io.teak.sdk.event.PushNotificationEvent;
import io.teak.sdk.event.PushRegistrationEvent;
import java.util.Map;

public class FCMPushProvider extends FirebaseMessagingService implements IPushProvider {
    private static FCMPushProvider Instance = null;

    private Context context;
    private FirebaseApp firebaseApp;

    public FCMPushProvider() {
        super();
        Instance = this;
    }

    public static FCMPushProvider initialize(@NonNull final Context context) throws IntegrationChecker.MissingDependencyException {
        IntegrationChecker.requireDependency("com.google.firebase.messaging.FirebaseMessagingService");

        if (Instance == null) {
            Teak.log.i("google.fcm.initialize", "Creating new FCMPushProvider instance.");
            Instance = new FCMPushProvider();
            Instance.context = context;
        } else {
            Teak.log.i("google.fcm.initialize", "FCMPushProvider already created.");

            Instance.context = context;
            if (Instance.getApplicationContext() != Instance.context) {
                Teak.log.e("google.fcm.initialize.context_mismatch",
                    Helpers.mm.h("getApplicationContext", Instance.getApplicationContext(),
                        "Instance.context", Instance.context));
            }
        }

        return Instance;
    }

    public void postEvent(final Context context, final Intent intent) {
        final TeakCore teakCore = TeakCore.getWithoutThrow(context);
        if (teakCore == null) {
            Teak.log.e("google.fcm.null_teak_core", "TeakCore.getWithoutThrow returned null.");
        }
        TeakEvent.postEvent(new PushNotificationEvent(PushNotificationEvent.Received, context, intent));
    }

    //// FirebaseMessagingService

    @Override
    public void onNewToken(String token) {
        if (token == null) {
            Teak.log.e("google.fcm.null_token", "Got null token from onNewToken.");
        } else {
            Teak.log.i("google.fcm.registered", Helpers.mm.h("fcmId", token));
            if (Teak.isEnabled()) {
                TeakEvent.postEvent(new PushRegistrationEvent("gcm_push_key", token));
            }
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Future-Pat, the RemoteMessage.toIntent method doesn't seem to exist all the time
        // so don't rely on it.
        final Intent intent = new Intent().putExtras(welcomeToTheBundle(remoteMessage.getData()));

        // Future-Pat, this method will only be invoked via an incoming message,
        // in which case getApplicationContext() will work
        this.postEvent(getApplicationContext(), intent);
    }

    //// IPushProvider

    @Override
    public void requestPushKey(@NonNull Map<String, Object> pushConfiguration) {
        // Future-Pat, this method will only be invoked via Teak SDK, so getApplicationContext()
        // will not work.
        final boolean ignoreDefaultFirebaseConfiguration = (boolean) pushConfiguration.getOrDefault("ignoreDefaultFirebaseConfiguration", false);

        if (this.firebaseApp == null) {
            // First try and get a Firebase App if it's already initialized
            try {
                this.firebaseApp = FirebaseApp.getInstance();
            } catch (Exception ignored) {
            }

            if (this.firebaseApp == null) {
                try {
                    // If FirebaseOptions.fromResource is not null, it's a good bet that someone is using
                    // the gradle plugin or something. In this case, we can safely create the default
                    // because subsequent calls to FirebaseApp.initializeApp will simply return the
                    // existing default.
                    if (FirebaseOptions.fromResource(context) != null) {
                        Teak.log.i("google.fcm.intialization", "FirebaseOptions.fromResource");
                        this.firebaseApp = FirebaseApp.initializeApp(this.context);
                    } else {
                        // No FirebaseOptions.fromResource means we're almost certainly responsible
                        // for all Firebase use and initialization.
                        Teak.log.i("google.fcm.intialization", pushConfiguration);
                        FirebaseOptions.Builder builder = new FirebaseOptions.Builder()
                                                              .setGcmSenderId((String) pushConfiguration.get("gcmSenderId"))
                                                              .setApplicationId((String) pushConfiguration.get("firebaseAppId"));
                        this.firebaseApp = FirebaseApp.initializeApp(this.context, builder.build());
                    }
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            }
        }

        if (this.firebaseApp == null) {
            Teak.log.e("google.fcm.null_app", "Could not get or create Firebase App. Push notifications are unlikely to work.");
        } else {
            try {
                final Task<InstanceIdResult> instanceIdTask = FirebaseInstanceId
                                                                  .getInstance()
                                                                  .getInstanceId();

                instanceIdTask.addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
                    @Override
                    public void onSuccess(InstanceIdResult instanceIdResult) {
                        final String registrationId = instanceIdResult.getToken();
                        Teak.log.i("google.fcm.registered", Helpers.mm.h("fcmId", registrationId));
                        if (Teak.isEnabled()) {
                            TeakEvent.postEvent(new PushRegistrationEvent("gcm_push_key", registrationId));
                        }
                    }
                });
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        }
    }

    ///// Beware of the Leopard

    /*
        //
        // com.google.firebase.messaging.RemoteMessage
        //
        public final Map<String, String> getData() {
            if (this.zzdt == null) {
                Bundle var1 = this.zzds;
                ArrayMap var2 = new ArrayMap();
                Iterator var3 = var1.keySet().iterator();

                while(var3.hasNext()) {
                    String var4 = (String)var3.next();
                    Object var5;
                    if ((var5 = var1.get(var4)) instanceof String) {
                        String var6 = (String)var5;
                        if (!var4.startsWith("google.") && !var4.startsWith("gcm.") && !var4.equals("from") && !var4.equals("message_type") && !var4.equals("collapse_key")) {
                            var2.put(var4, var6);
                        }
                    }
                }

                this.zzdt = var2;
            }

            return this.zzdt;
        }
     */

    private static Bundle welcomeToTheBundle(Map<String, String> remoteMessageGetDataResult) {
        final Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : remoteMessageGetDataResult.entrySet()) {
            bundle.putString(entry.getKey(), entry.getValue());
        }
        return bundle;
    }
}
