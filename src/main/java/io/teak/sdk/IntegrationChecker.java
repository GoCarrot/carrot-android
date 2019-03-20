package io.teak.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.teak.sdk.configuration.AppConfiguration;
import io.teak.sdk.configuration.RemoteConfiguration;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.io.ManifestParser;

public class IntegrationChecker {
    public static final String LOG_TAG = "Teak.Integration";

    private final Activity activity;

    private static final Map<String, String> errorsToReport = new HashMap<>();

    public static final String[][] dependencies = new String[][] {
        new String[] {"android.support.v4.content.LocalBroadcastManager", "com.android.support:support-core-utils:26+"},
        new String[] {"android.support.v4.app.NotificationManagerCompat", "com.android.support:support-compat:26+"},
        new String[] {"com.google.android.gms.common.GooglePlayServicesUtil", "com.google.android.gms:play-services-base:16+", "com.google.android.gms:play-services-basement:16+"},
        new String[] {"com.google.firebase.messaging.FirebaseMessagingService", "com.google.firebase:firebase-messaging:17+"},
        new String[] {"com.google.android.gms.ads.identifier.AdvertisingIdClient", "com.google.android.gms:play-services-ads:16+"}};

    public static final String[] permissionFeatures = new String[] {
        "shortcutbadger"};
    public static final String[][] permissions = new String[][] {
        new String[] {
            "com.sec.android.provider.badge.permission.READ",
            "com.sec.android.provider.badge.permission.WRITE",
            "com.htc.launcher.permission.READ_SETTINGS",
            "com.htc.launcher.permission.UPDATE_SHORTCUT",
            "com.sonyericsson.home.permission.BROADCAST_BADGE",
            "com.sonymobile.home.permission.PROVIDER_INSERT_BADGE",
            "com.anddoes.launcher.permission.UPDATE_COUNT",
            "com.majeur.launcher.permission.UPDATE_BADGE",
            "com.huawei.android.launcher.permission.CHANGE_BADGE",
            "com.huawei.android.launcher.permission.READ_SETTINGS",
            "com.huawei.android.launcher.permission.WRITE_SETTINGS",
            "android.permission.READ_APP_BADGE",
            "com.oppo.launcher.permission.READ_SETTINGS",
            "com.oppo.launcher.permission.WRITE_SETTINGS",
            "me.everything.badger.permission.BADGE_COUNT_READ",
            "me.everything.badger.permission.BADGE_COUNT_WRITE"}};

    public static final String[] configurationStrings = new String[] {
        AppConfiguration.TEAK_API_KEY_RESOURCE,
        AppConfiguration.TEAK_APP_ID_RESOURCE};

    public static void requireDependency(@NonNull String fullyQualifiedClassName) throws MissingDependencyException {
        addDependency(fullyQualifiedClassName, true);
    }

    public static void suggestDependency(@NonNull String fullyQualifiedClassName) throws MissingDependencyException {
        addDependency(fullyQualifiedClassName, false);
    }

    private static void addDependency(@NonNull String fullyQualifiedClassName, boolean required) throws MissingDependencyException {
        // Protect against future-Pat adding/removing a dependency and forgetting to update the array
        if (BuildConfig.DEBUG) {
            boolean foundInDependencies = false;
            for (String[] dependency : dependencies) {
                if (fullyQualifiedClassName.equals(dependency[0])) {
                    foundInDependencies = true;
                    break;
                }
            }
            if (!foundInDependencies) {
                throw new NoClassDefFoundError("Missing '" + fullyQualifiedClassName + "' in dependencies list.");
            }
        }

        try {
            Class.forName(fullyQualifiedClassName);
        } catch (ClassNotFoundException e) {
            final String dependency = "Missing dependency: " + fullyQualifiedClassName;
            if (required) {
                addErrorToReport("dependency.required", dependency);
                throw new MissingDependencyException(e);
            } else {
                addErrorToReport("dependency.optional", dependency);
            }
        }
    }

    public static class InvalidConfigurationException extends Exception {
        public InvalidConfigurationException(@NonNull String message) {
            super(message);
        }
    }

    public static class UnsupportedVersionException extends Exception {
        public UnsupportedVersionException(@NonNull String message) {
            super(message);
        }
    }

    public static class MissingDependencyException extends ClassNotFoundException {
        public final String[] missingDependency;

        public MissingDependencyException(@NonNull ClassNotFoundException e) {
            super();

            // See what the missing dependency is
            String[] foundMissingDependencies = null;
            for (String[] dependency : dependencies) {
                if (e.getMessage().contains(dependency[0])) {
                    foundMissingDependencies = dependency;
                    break;
                }
            }
            this.missingDependency = foundMissingDependencies;

            // Add to list that will get reported during debug
            if (this.missingDependency != null) {
                addErrorToReport(this.missingDependency[0],
                    "Missing dependencies: " + Helpers.join(", ",
                                                   Arrays.copyOfRange(this.missingDependency, 1, this.missingDependency.length)));
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private static IntegrationChecker integrationChecker;

    static boolean init(@NonNull Activity activity) {
        if (integrationChecker == null || integrationChecker.activity != activity) {
            try {
                integrationChecker = new IntegrationChecker(activity);
                return true;
            } catch (Exception e) {
                android.util.Log.e(LOG_TAG, android.util.Log.getStackTraceString(e));
                Teak.log.exception(e);
            }
        }
        return false;
    }

    private IntegrationChecker(@NonNull Activity activity) throws UnsupportedVersionException {
        this.activity = activity;

        // Teak 2.0+ requires targeting Android 26+
        if (Helpers.getTargetSDKVersion(activity) < Build.VERSION_CODES.O) {
            throw new UnsupportedVersionException("Teak only supports targetSdkVersion 26 or higher.");
        }

        // Check for configuration strings
        try {
            final String packageName = this.activity.getPackageName();
            final ApplicationInfo appInfo = this.activity.getPackageManager().getApplicationInfo(this.activity.getPackageName(), PackageManager.GET_META_DATA);
            final Bundle metaData = appInfo.metaData;

            for (String configString : configurationStrings) {
                try {
                    // First try AndroidManifest meta data
                    boolean foundConfigString = false;
                    if (metaData != null) {
                        final String appIdFromMetaData = metaData.getString(configString);
                        if (appIdFromMetaData != null && appIdFromMetaData.startsWith("teak")) {
                            foundConfigString = true;
                        }
                    }

                    // Default to resource id
                    if (!foundConfigString) {
                        int resId = this.activity.getResources().getIdentifier(configString, "string", packageName);
                        this.activity.getString(resId);
                    }
                } catch (Exception ignored) {
                    addErrorToReport("R.string." + configString, "Failed to find R.string." + configString);
                }
            }
        } catch (Exception ignored) {
        }

        // Check all dependencies, they'll add themselves to the missing list
        for (String[] dependency : dependencies) {
            try {
                IntegrationChecker.requireDependency(dependency[0]);
            } catch (Exception ignored) {
            }
        }

        // Run checks on a background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                // If the target SDK version is 26+ the linked support-v4 lib must be 26.1.0+
                checkSupportv4Version();

                // Activity launch mode should be 'singleTask', 'singleTop' or 'singleInstance'
                checkActivityLaunchMode();

                // Manifest checks
                checkAndroidManifest();
            }
        })
            .start();

        TeakEvent.addEventListener(new TeakEvent.EventListener() {
            @Override
            public void onNewEvent(@NonNull TeakEvent event) {
                if (event.eventType.equals(RemoteConfigurationEvent.Type)) {
                    final RemoteConfiguration remoteConfiguration = ((RemoteConfigurationEvent) event).remoteConfiguration;
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            onRemoteConfigurationReady(remoteConfiguration);
                        }
                    });
                }
            }
        });
    }

    private void checkActivityLaunchMode() {
        // Check the launch mode of the activity for debugging purposes
        try {
            ComponentName cn = new ComponentName(this.activity, this.activity.getClass());
            ActivityInfo ai = this.activity.getPackageManager().getActivityInfo(cn, PackageManager.GET_META_DATA);
            // (LAUNCH_SINGLE_INSTANCE == LAUNCH_SINGLE_TASK | LAUNCH_SINGLE_TOP) but let's not
            // assume that those values will stay the same
            if ((ai.launchMode & ActivityInfo.LAUNCH_SINGLE_INSTANCE) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TASK) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TOP) == 0) {
                addErrorToReport("activity.launchMode", "The android:launchMode of this <activity> is not set to 'singleTask', 'singleTop' or 'singleInstance'. This could cause undesired behavior.");
            }
        } catch (Exception ignored) {
        }
    }

    private void checkAndroidManifest() {
        try {
            final TeakConfiguration teakConfiguration = TeakConfiguration.get();
            final ManifestParser manifestParser = new ManifestParser(this.activity);

            // Check to make sure there is only one <application>
            final List<ManifestParser.XmlTag> applications = manifestParser.tags.find("$.application");
            if (applications.size() > 1) {
                addErrorToReport("application.count", "There is more than one <application> defined in your AndroidManifest.xml, only one is allowed by Android.");
            }

            // Make sure the Teak FCM service is present
            final List<ManifestParser.XmlTag> fcmServices = applications.get(0).find("service.intent-filter.action",
                new HashMap.SimpleEntry<>("name", "com.google.firebase.MESSAGING_EVENT"));
            ManifestParser.XmlTag teakFcmService = null;
            for (ManifestParser.XmlTag tag : fcmServices) {
                final String checkServiceClass = tag.attributes.get("name");
                try {
                    Class.forName(checkServiceClass);
                } catch (Exception ignored) {
                    addErrorToReport(checkServiceClass, "Push notifications will crash because \"" + checkServiceClass + "\" is in your AndroidManifest.xml, but the corresponding SDK has been removed.\n\nTo fix this, remove the <service> for \"" + checkServiceClass + "\"");
                }

                // Check to make sure Teak GCM receiver is present
                if ("io.teak.sdk.push.FCMPushProvider".equals(checkServiceClass)) {
                    teakFcmService = tag;
                }
            }

            // Error if no Teak GCM receiver
            if (teakFcmService == null) {
                addErrorToReport("io.teak.sdk.push.FCMPushProvider", "Push notifications will not work because there is no \"io.teak.sdk.push.FCMPushProvider\" <service> in your AndroidManifest.xml.\n\nTo fix this, add the io.teak.sdk.push.FCMPushProvider <service>");
            }

            // Check to make sure the Teak job service is present
            final List<ManifestParser.XmlTag> teakDeviceStateService = applications.get(0).find("service",
                new HashMap.SimpleEntry<>("name", "io.teak.sdk.service.JobService"));
            if (teakDeviceStateService.size() < 1) {
                addErrorToReport("io.teak.sdk.service.JobService", "Animated notifications will not work on Android 8+ because there is no \"io.teak.sdk.service.DeviceStateService\" <service> in your AndroidManifest.xml.\n\nTo fix this, add the \"io.teak.sdk.service.DeviceStateService\" <service>");
            }

            // Find the teakXXXX:// scheme
            final List<ManifestParser.XmlTag> teakScheme = applications.get(0).find("(activity|activity\\-alias).intent\\-filter.data",
                new HashMap.SimpleEntry<>("scheme", "teak\\d+"));
            if (teakScheme.size() < 1) {
                addErrorToReport("activity.intent-filter.data.scheme", "Deep linking will not work because there is no <intent-filter> in any <activity> or <activity-alias> has the \"teak\" data scheme.\n\nAdd <data android:scheme=\"teak" + teakConfiguration.appConfiguration.appId + "\" android:host=\"*\" /> to the <intent-filter> for your main activity.");
            } else {
                // Make sure the <intent-filter> for the teakXXXX:// scheme has <action android:name="android.intent.action.VIEW" />
                final List<ManifestParser.XmlTag> teakSchemeAction = teakScheme.get(0).find("intent\\-filter.action",
                    new HashMap.SimpleEntry<>("name", "android.intent.action.VIEW"));
                if (teakSchemeAction.size() < 1) {
                    addErrorToReport("activity.intent-filter.data.scheme", "the <intent-filter> with the \"teak\" data scheme should have <action android:name=\"android.intent.action.VIEW\" />");
                }

                // Make sure the <intent-filter> for the teakXXXX:// scheme has <category android:name="android.intent.category.DEFAULT" /> and <category android:name="android.intent.category.BROWSABLE" />
                final List<ManifestParser.XmlTag> teakSchemeCategories = teakScheme.get(0).find("intent\\-filter.category",
                    new HashMap.SimpleEntry<>("name", "android.intent.category.(DEFAULT|BROWSABLE)"));
                if (teakSchemeCategories.size() < 2) {
                    addErrorToReport("activity.intent-filter.data.scheme", "the <intent-filter> with the \"teak\" data scheme should have <category android:name=\"android.intent.category.DEFAULT\" /> and <category android:name=\"android.intent.category.BROWSABLE\" />");
                }

                // Make sure the <intent-filter> for the teakXXXX:// scheme does *not* also contain any http(s) schemes
                final List<ManifestParser.XmlTag> teakSchemeOtherSchemes = teakSchemeCategories.get(0).find("data",
                    new HashMap.SimpleEntry<>("scheme", "(http|https)"));
                if (teakSchemeOtherSchemes.size() > 0) {
                    addErrorToReport("activity.intent-filter.data.scheme", "the <intent-filter> with the \"teak\" data scheme *should not* contain any http or https schemes.\n\nPut the \"teak\" data scheme in its own <intent-filter>");
                }

                // Make sure per-feature permissions are included
                final List<ManifestParser.XmlTag> usesPermissions = manifestParser.tags.find("$.uses-permission");
                final Map<String, Boolean> permissionsAsMap = new HashMap<>();
                for (ManifestParser.XmlTag permission : usesPermissions) {
                    permissionsAsMap.put(permission.attributes.get("name"), true);
                }

                for (int i = 0; i < IntegrationChecker.permissionFeatures.length; i++) {
                    final String feature = IntegrationChecker.permissionFeatures[i];
                    final List<Integer> missingPermissions = new ArrayList<>();
                    for (int j = 0; j < IntegrationChecker.permissions[i].length; i++) {
                        final String permission = IntegrationChecker.permissions[i][j];
                        if (!permissionsAsMap.containsKey(permission)) {
                            missingPermissions.add(j);
                        }
                    }

                    for (int j = 0; j < missingPermissions.size(); j++) {
                        addErrorToReport("permission." + feature, "missing permission '" + IntegrationChecker.permissions[i][missingPermissions.get(j)] + "'");
                    }
                }
                for (ManifestParser.XmlTag permission : usesPermissions) {
                    Teak.log.i("permission", permission.toString());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void onRemoteConfigurationReady(@NonNull RemoteConfiguration remoteConfiguration) {
        // If Enhanced Integration Checks are enabled, report the errors as alert dialogs
        if (remoteConfiguration.enhancedIntegrationChecks) {
            for (Map.Entry<String, String> error : errorsToReport.entrySet()) {
                AlertDialog.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder = new AlertDialog.Builder(this.activity, android.R.style.Theme_Material_Dialog_Alert);
                } else {
                    builder = new AlertDialog.Builder(this.activity);
                }
                builder.setTitle("Human, your assistance is needed")
                    .setMessage(error.getValue())
                    .setPositiveButton(android.R.string.yes, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            }
        }
    }

    private static void addErrorToReport(@NonNull String key, @NonNull String description) {
        errorsToReport.put(key, description);
        android.util.Log.e(LOG_TAG, description);
    }

    private void checkSupportv4Version() {
        try {
            final ApplicationInfo appInfo = this.activity.getPackageManager().getApplicationInfo(this.activity.getPackageName(), PackageManager.GET_META_DATA);
            final int targetSdkVersion = appInfo.targetSdkVersion;
            try {
                Class<?> notificationCompatBuilderClass = Class.forName("android.support.v4.app.NotificationCompat$Builder");
                notificationCompatBuilderClass.getMethod("setChannelId", String.class);
            } catch (ClassNotFoundException ignored) {
                // This is fine, it will get caught by the
            } catch (Exception ignored) {
                addErrorToReport("support-v4.less-than.26.1", "App is targeting SDK version " + targetSdkVersion +
                                                                  " but support-v4 library needs to be updated to at least version 26.1.0 to support notification categories.");
            }
        } catch (Exception ignored) {
        }
    }
}
