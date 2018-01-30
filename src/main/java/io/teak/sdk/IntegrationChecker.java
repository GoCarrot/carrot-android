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

package io.teak.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.teak.sdk.configuration.RemoteConfiguration;
import io.teak.sdk.event.RemoteConfigurationEvent;
import io.teak.sdk.io.ManifestParser;

public class IntegrationChecker {
    private final Activity activity;

    private static final Map<String, String> errorsToReport = new HashMap<>();

    public static final String[][] dependencies = new String[][] {
            new String[] {"android.support.v4.content.LocalBroadcastManager", "com.android.support:support-core-utils:26+"},
            new String[] {"android.support.v4.app.NotificationManagerCompat", "com.android.support:support-compat:26+"},
            new String[] {"com.google.android.gms.common.GooglePlayServicesUtil", "com.google.android.gms:play-services-base:10+", "com.google.android.gms:play-services-basement:10+"},
            new String[] {"com.google.android.gms.gcm.GoogleCloudMessaging", "com.google.android.gms:play-services-gcm:10+"},
            new String[] {"com.google.android.gms.iid.InstanceIDListenerService", "com.google.android.gms:play-services-iid:10+"}
    };

    public static void requireDependency(@NonNull String fullyQualifiedClassName) throws MissingDependencyException {
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
                throw new RuntimeException("Missing '" + fullyQualifiedClassName + "' in dependencies list.");
            }
        }

        try {
            Class.forName(fullyQualifiedClassName);
        } catch (ClassNotFoundException e) {
            throw new MissingDependencyException(e);
        }
    }

    public static void requireManifestDefinition(@NonNull String type, @NonNull String fullyQualifiedClass) {
        /*// Check to make sure that InstanceIDListenerService is in the manifest
        boolean foundInstanceIdListenerService = false;
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SERVICES);
            for (ServiceInfo serviceInfo : packageInfo.services) {
                if ("io.teak.sdk.InstanceIDListenerService".equals(serviceInfo.name)) {
                    foundInstanceIdListenerService = true;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        if (!foundInstanceIdListenerService) {
            throw new ServiceConfigurationError("io.teak.sdk.InstanceIDListenerService not found in AndroidManifest");
        }*/
    }

    public static class MissingDependencyException extends ClassNotFoundException {
        public final String[] missingDependency;
        public MissingDependencyException(@NonNull ClassNotFoundException e) {
            super();
            initCause(e);

            // See what the missing dependency is
            String[] foundMissingDependencies = null;
            for (String[] dependency : dependencies) {
                if (dependency[0].equals(e.getMessage())) {
                    foundMissingDependencies = dependency;
                    break;
                }
            }
            this.missingDependency = foundMissingDependencies;

            // Add to list that will get reported during debug
            if (this.missingDependency != null) {
                errorsToReport.put(this.missingDependency[0],
                        "Missing dependencies: " + TextUtils.join(", ",
                                Arrays.copyOfRange(this.missingDependency, 1, this.missingDependency.length)));
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private static IntegrationChecker integrationChecker;
    static boolean init(@NonNull Activity activity) {
        if (integrationChecker == null) {
            try {
                integrationChecker = new IntegrationChecker(activity);
                return true;
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        }
        return false;
    }

    private IntegrationChecker(@NonNull Activity activity) {
        this.activity = activity;

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
        }).start();

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

    void checkActivityLaunchMode() {
        // Check the launch mode of the activity for debugging purposes
        try {
            ComponentName cn = new ComponentName(this.activity, this.activity.getClass());
            ActivityInfo ai = this.activity.getPackageManager().getActivityInfo(cn, PackageManager.GET_META_DATA);
            // (LAUNCH_SINGLE_INSTANCE == LAUNCH_SINGLE_TASK | LAUNCH_SINGLE_TOP) but let's not
            // assume that those values will stay the same
            if ((ai.launchMode & ActivityInfo.LAUNCH_SINGLE_INSTANCE) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TASK) == 0 &&
                (ai.launchMode & ActivityInfo.LAUNCH_SINGLE_TOP) == 0) {
                errorsToReport.put("activity.launchMode", "The android:launchMode of this <activity> is not set to 'singleTask', 'singleTop' or 'singleInstance'. This could cause undesired behavior.");
            }
        } catch (Exception ignored) {
        }
    }

    private void checkAndroidManifest() {
        try {
            final ManifestParser manifestParser = new ManifestParser(this.activity);

            // Check to make sure there is only one <application>
            final List<ManifestParser.XmlTag> applications = manifestParser.tags.find("$.application");
            if (applications.size() > 1) {
                errorsToReport.put("application.count", "There is more than one <application> defined in your AndroidManifest.xml, only one is allowed by Android.");
            }

            // Check for receivers pointing to classes that don't exist
            final List<ManifestParser.XmlTag> receivers = applications.get(0).find("receiver.intent-filter.action",
                    new HashMap.SimpleEntry<>("name", "com.google.android.c2dm.intent.RECEIVE"));
            for (ManifestParser.XmlTag tag : receivers) {
                final String checkReceiverClass = tag.attributes.get("name");
                try {
                    Class.forName(checkReceiverClass);
                } catch (Exception ignored) {
                    errorsToReport.put(checkReceiverClass, "\"" + checkReceiverClass + "\" is in your AndroidManifest.xml, but the corresponding SDK has been removed.\n\nRemove the <receiver> for \"" + checkReceiverClass + "\"");
                }
            }

            // Check to make sure that their android.intent.action.MAIN has the Teak intent filters
            final List<ManifestParser.XmlTag> mainActivity = applications.get(0).find("activity.intent\\-filter.action",
                    new HashMap.SimpleEntry<>("name", "android.intent.action.MAIN"));
            if (mainActivity.size() < 1) {
                errorsToReport.put("android.intent.action.MAIN", "None of the <activity> in AndroidManifest.xml has the \"android.intent.action.MAIN\" <action>.");
            } else {
                // Find the teakXXXX:// scheme
                final List<ManifestParser.XmlTag> teakScheme = mainActivity.get(0).find("intent\\-filter.data",
                        new HashMap.SimpleEntry<>("scheme", "teak\\d+"));
                if (teakScheme.size() < 1) {
                    errorsToReport.put("activity.intent-filter.data.scheme", "None of the <intent-filter> in your main <activity> has the \"teak\" data scheme.");
                } else {
                    // Make sure the <intent-filter> for the teakXXXX:// scheme has <action android:name="android.intent.action.VIEW" />
                    final List<ManifestParser.XmlTag> teakSchemeAction = teakScheme.get(0).find("action",
                            new HashMap.SimpleEntry<>("name", "android.intent.action.VIEW"));
                    if (teakSchemeAction.size() < 1) {
                        errorsToReport.put("activity.intent-filter.data.scheme", "the <intent-filter> with the \"teak\" data scheme should have <action android:name=\"android.intent.action.VIEW\" />");
                    }

                    // Make sure the <intent-filter> for the teakXXXX:// scheme has <category android:name="android.intent.category.DEFAULT" /> and <category android:name="android.intent.category.BROWSABLE" />
                    final List<ManifestParser.XmlTag> teakSchemeCategories = teakScheme.get(0).find("category",
                            new HashMap.SimpleEntry<>("name", "android.intent.category.(DEFAULT|BROWSABLE)"));
                    if (teakSchemeCategories.size() < 2) {
                        errorsToReport.put("activity.intent-filter.data.scheme", "the <intent-filter> with the \"teak\" data scheme should have <category android:name=\"android.intent.category.DEFAULT\" /> and <category android:name=\"android.intent.category.BROWSABLE\" />");
                    }
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

    private void checkSupportv4Version() {
        try {
            final ApplicationInfo appInfo = this.activity.getPackageManager().getApplicationInfo(this.activity.getPackageName(), PackageManager.GET_META_DATA);
            final int targetSdkVersion = appInfo.targetSdkVersion;
            if (targetSdkVersion >= Build.VERSION_CODES.O) {
                try {
                    Class<?> notificationCompatBuilderClass = NotificationCompat.Builder.class;
                    notificationCompatBuilderClass.getMethod("setChannelId", String.class);
                } catch (Exception ignored) {
                    errorsToReport.put("support-v4.less-than.26.1", "App is targeting SDK version " + targetSdkVersion +
                            " but support-v4 library needs to be updated to at least version 26.1.0 to support notification categories.");
                }
            }
        } catch (Exception ignored) {
        }
    }
}
