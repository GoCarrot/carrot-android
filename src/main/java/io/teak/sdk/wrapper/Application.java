package io.teak.sdk.wrapper;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import io.teak.sdk.Teak;
import io.teak.sdk.Unobfuscable;

@SuppressWarnings("unused")
public class Application extends android.app.Application implements Unobfuscable {
    ActivityLifecycleCallbacks lifecycleCallbacks;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        final String appEntryCanonicalName = base.getPackageName() + ".AppEntry";

        if (lifecycleCallbacks == null) {
            lifecycleCallbacks = new ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle bundle) {
                    if (activity.getClass().getCanonicalName().equalsIgnoreCase(appEntryCanonicalName)) {
                        Teak.onCreate(activity);
                        if (Teak.Instance != null) {
                            Teak.Instance.lifecycleCallbacks.onActivityCreated(activity, bundle);
                        }
                    }
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    // None
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    // None
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    // None
                }

                @Override
                public void onActivityStopped(Activity activity) {
                    // None
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
                    // None
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    // None
                }
            };
            registerActivityLifecycleCallbacks(lifecycleCallbacks);
        }
    }
}
