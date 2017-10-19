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

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import io.teak.sdk.io.DefaultAndroidDeviceInfo;
import io.teak.sdk.io.DefaultAndroidNotification;
import io.teak.sdk.io.DefaultAndroidResources;
import io.teak.sdk.io.IAndroidDeviceInfo;
import io.teak.sdk.io.IAndroidNotification;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.push.IPushProvider;
import io.teak.sdk.store.IStore;

public class DefaultObjectFactory implements IObjectFactory {
    private final Context context;
    private final DefaultAndroidResources androidResources;
    private final IStore store;
    private final IAndroidDeviceInfo androidDeviceInfo;
    private final IPushProvider pushProvider;
    private final IAndroidNotification androidNotification;

    DefaultObjectFactory(@NonNull Context context) {
        this.context = context;
        this.androidResources = new DefaultAndroidResources(this.context);
        this.store = createStore(context);
        this.androidDeviceInfo = new DefaultAndroidDeviceInfo(this.context);
        this.pushProvider = createPushProvider(context);
        this.androidNotification = new DefaultAndroidNotification(this.context);
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

    ///// Helpers

    private IStore createStore(@NonNull Context context) {
        final PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            Teak.log.e("factory.istore", "Unable to get Package Manager.");
            return null;
        }

        final String bundleId = this.context.getPackageName();
        if (bundleId == null) {
            Teak.log.e("factory.istore", "Unable to get Bundle Id.");
            return null;
        }

        final String installerPackage = packageManager.getInstallerPackageName(bundleId);

        // Applicable store
        if (installerPackage != null) {
            Class<?> clazz = null;
            if (installerPackage.equals("com.amazon.venezia")) {
                try {
                    clazz = Class.forName("com.amazon.device.iap.PurchasingListener");
                } catch (Exception e) {
                    Teak.log.exception(e);
                }

                if (clazz != null) {
                    try {
                        clazz = Class.forName("io.teak.sdk.Amazon");
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }
                }
            } else {
                // Default to Google Play
                try {
                    clazz = Class.forName("io.teak.sdk.GooglePlay");
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            }

            try {
                return (IStore) (clazz != null ? clazz.newInstance() : null);
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        } else {
            Teak.log.e("factory.istore", "Installer package (Store) is null, purchase tracking disabled.");
        }

        return null;
    }

    // This is used
    public static IPushProvider createPushProvider(@NonNull Context context) {
        return null;
    }
}
