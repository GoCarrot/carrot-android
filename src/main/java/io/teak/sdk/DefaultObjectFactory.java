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

import io.teak.sdk.io.DefaultAndroidResources;
import io.teak.sdk.io.IAndroidResources;
import io.teak.sdk.store.IStore;

class DefaultObjectFactory implements IObjectFactory {
    @Nullable
    @Override
    public IStore getIStore(Context context) {
        if (this.iStore == null) {
            PackageManager packageManager = context.getPackageManager();
            if (packageManager == null) {
                Teak.log.e("factory.istore", "Unable to get Package Manager.");
                return null;
            }

            String bundleId = context.getPackageName();
            if (bundleId == null) {
                Teak.log.e("factory.istore", "Unable to get Bundle Id.");
                return null;
            }

            String installerPackage = packageManager.getInstallerPackageName(bundleId);

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
                    this.iStore = (IStore) (clazz != null ? clazz.newInstance() : null);
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            } else {
                Teak.log.e("factory.istore", "Installer package (Store) is null, purchase tracking disabled.");
            }
        }

        return this.iStore;
    }
    private IStore iStore;

    @NonNull
    @Override
    public IAndroidResources getAndroidResources(Context context) {
        if (this.defaultAndroidResources == null) {
            this.defaultAndroidResources = new DefaultAndroidResources(context);
        }
        return this.defaultAndroidResources;
    }
    private DefaultAndroidResources defaultAndroidResources;
}
