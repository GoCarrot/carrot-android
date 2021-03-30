package io.teak.sdk;

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.amazon.device.messaging.ADM;
import io.teak.sdk.core.ITeakCore;
import io.teak.sdk.core.TeakCore;
import io.teak.sdk.io.DefaultAndroidDeviceInfo;
import io.teak.sdk.io.DefaultAndroidNotification;
import io.teak.sdk.io.DefaultAndroidResources;
import io.teak.sdk.io.IAndroidDeviceInfo;
import io.teak.sdk.io.IAndroidNotification;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.push.ADMPushProvider;
import io.teak.sdk.push.FCMPushProvider;
import io.teak.sdk.push.IPushProvider;
import io.teak.sdk.store.IStore;
import io.teak.sdk.support.ILocalBroadcastManager;
import io.teak.sdk.support.INotificationBuilder;
import io.teak.sdk.support.INotificationManager;

public class DefaultObjectFactory implements IObjectFactory {
    private final IAndroidResources androidResources;
    private final IStore store;
    private final IAndroidDeviceInfo androidDeviceInfo;
    private final IPushProvider pushProvider;
    private final IAndroidNotification androidNotification;
    private final ITeakCore teakCore;

    DefaultObjectFactory(@NonNull Context context) throws IntegrationChecker.MissingDependencyException {
        // Suggest AndroidX, require support-v4
        IntegrationChecker.suggestButRequireDependency("androidx.localbroadcastmanager.content.LocalBroadcastManager",
            "android.support.v4.content.LocalBroadcastManager");
        IntegrationChecker.suggestButRequireDependency("androidx.core.app.NotificationCompat",
            "android.support.v4.app.NotificationManagerCompat");
        IntegrationChecker.suggestButRequireDependency("androidx.core.app.NotificationManagerCompat",
            "android.support.v4.app.NotificationManagerCompat");

        this.androidResources = new DefaultAndroidResources(context);
        this.store = createStore(context);
        this.androidDeviceInfo = new DefaultAndroidDeviceInfo(context);
        this.androidNotification = DefaultAndroidNotification.get(context);
        this.teakCore = TeakCore.get(context);

        // Future-Pat, this is handled differently because it can be the case where someone just uploads
        // their Google Play build to Amazon, in which case things can go wrong, and Teak should just
        // ignore this instead of disabling itself.
        IPushProvider tempPushProvider = null;
        try {
            tempPushProvider = createPushProvider(context);
        } catch (Exception ignored) {
        }
        this.pushProvider = tempPushProvider;
    }

    ///// IObjectFactory

    @Nullable
    @Override
    public IStore getIStore() {
        return this.store;
    }

    @NonNull
    @Override
    public IAndroidResources getAndroidResources() {
        return this.androidResources;
    }

    @NonNull
    @Override
    public IAndroidDeviceInfo getAndroidDeviceInfo() {
        return this.androidDeviceInfo;
    }

    @Nullable
    @Override
    public IPushProvider getPushProvider() {
        return this.pushProvider;
    }

    @NonNull
    @Override
    public IAndroidNotification getAndroidNotification() {
        return this.androidNotification;
    }

    @NonNull
    @Override
    public ITeakCore getTeakCore() {
        return this.teakCore;
    }

    ///// Helpers

    private IStore createStore(@NonNull Context context) {
        final PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            Teak.log.e("factory.istore", "Unable to get Package Manager.");
            return null;
        }

        final String bundleId = context.getPackageName();
        if (bundleId == null) {
            Teak.log.e("factory.istore", "Unable to get Bundle Id.");
            return null;
        }

        // Applicable store, default to GooglePlay
        Class<?> clazz = io.teak.sdk.store.GooglePlay.class;
        if (Helpers.isAmazonDevice(context)) {
            try {
                Class.forName("com.amazon.device.iap.PurchasingListener");
                clazz = io.teak.sdk.store.Amazon.class;
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        }

        try {
            return (IStore) clazz.newInstance();
        } catch (Exception e) {
            Teak.log.exception(e);
        }

        return null;
    }

    public static INotificationManager createNotificationManager(@NonNull Context context) {
        try {
            Class.forName("androidx.core.app.NotificationManagerCompat");
            return new io.teak.sdk.support.androidx.NotificationManager(context);
        } catch (Exception ignored) {
        }

        try {
            Class.forName("android.support.v4.app.NotificationManagerCompat");
            //            return new io.teak.sdk.support.v4.NotificationManager(context);
        } catch (Exception ignored) {
        }

        return null;
    }

    public static ILocalBroadcastManager createLocalBroadcastManager(@NonNull Context context) {
        try {
            Class.forName("androidx.localbroadcastmanager.content.LocalBroadcastManager");
            return new io.teak.sdk.support.androidx.LocalBroadcastManager(context);
        } catch (Exception ignored) {
        }

        try {
            Class.forName("android.support.v4.content.LocalBroadcastManager");
            //return new io.teak.sdk.support.v4.LocalBroadcastManager(context);
        } catch (Exception ignored) {
        }

        return null;
    }

    public static INotificationBuilder createNotificationBuilder(@NonNull Context context, @NonNull String notificationChannelId) {
        try {
            Class.forName("androidx.core.app.NotificationCompat");
            return new io.teak.sdk.support.androidx.NotificationBuilder(context, notificationChannelId);
        } catch (Exception ignored) {
        }

        try {
            Class.forName("android.support.v4.app.NotificationManagerCompat");
            //            return new io.teak.sdk.support.v4.NotificationBuilder(context, notificationChannelId);
        } catch (Exception ignored) {
        }

        // TODO: Integration checker things here.
        return null;
    }

    @SuppressWarnings("WeakerAccess") // Integration tests call this
    public static IPushProvider createPushProvider(@NonNull Context context) throws IntegrationChecker.MissingDependencyException {
        IPushProvider ret = null;
        IntegrationChecker.MissingDependencyException pushCreationException = null;
        try {
            Class.forName("com.amazon.device.messaging.ADM");
            if (new ADM(context).isSupported()) {
                ADMPushProvider admPushProvider = new ADMPushProvider();
                admPushProvider.initialize(context);
                ret = admPushProvider;
                Teak.log.i("factory.pushProvider", Helpers.mm.h("type", "adm"));
            }
        } catch (Exception ignored) {
        }

        if (ret == null) {
            try {
                Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
                try {
                    ret = FCMPushProvider.initialize(context);
                    Teak.log.i("factory.pushProvider", Helpers.mm.h("type", "fcm"));
                } catch (IntegrationChecker.MissingDependencyException e) {
                    pushCreationException = e;
                }
            } catch (Exception ignored) {
            }
        }

        if (ret == null) {
            Teak.log.e("factory.pushProvider", Helpers.mm.h("type", "none"));
            if (pushCreationException != null) {
                throw pushCreationException;
            }
        }
        return ret;
    }
}
