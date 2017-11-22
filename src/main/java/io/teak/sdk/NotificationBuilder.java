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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.net.ssl.SSLException;

public class NotificationBuilder {
    public static Notification createNativeNotification(Context context, Bundle bundle, TeakNotification teakNotificaton) {
        if (teakNotificaton.notificationVersion == TeakNotification.TEAK_NOTIFICATION_V0) {
            return createNativeNotificationV0(context, bundle, teakNotificaton);
        }

        try {
            return createNativeNotificationV1Plus(context, bundle, teakNotificaton);
        } catch (Exception e) {
            HashMap<String, Object> extras = new HashMap<>();
            if (teakNotificaton.teakCreativeName != null) {
                extras.put("teakCreativeName", teakNotificaton.teakCreativeName);
            }
            Teak.log.exception(e, extras);
            // TODO: Report to the 'callback' URL on the push when/if we implement that
            return null;
        }
    }

    private static String notificationChannelId;
    private static String getNotificationChannelId(Context context) {
        // Notification channel, required for targeting API 26+
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationChannelId == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            try {
                int importance = NotificationManager.IMPORTANCE_HIGH;
                String id = "teak";
                NotificationChannel channel = new NotificationChannel(id, "Notifications", importance);
                channel.enableLights(true);
                channel.setLightColor(Color.RED);
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[] {100, 200, 300, 400, 500, 400, 300, 200, 400});
                notificationManager.createNotificationChannel(channel);
                notificationChannelId = id;
            } catch (Exception ignored) {
            }
        }
        return notificationChannelId;
    }

    private static NotificationCompat.Builder getNotificationCompatBuilder(Context context) {
        NotificationCompat.Builder builder;
        if (TeakConfiguration.get().appConfiguration.targetSdkVersion >= Build.VERSION_CODES.O) {
            builder = new NotificationCompat.Builder(context, getNotificationChannelId(context));
            builder.setGroup(UUID.randomUUID().toString());
        } else {
            //noinspection deprecation
            builder = new NotificationCompat.Builder(context);
        }
        return builder;
    }

    private static Notification createNativeNotificationV1Plus(final Context context, Bundle bundle, final TeakNotification teakNotificaton) throws Exception {
        // Because we can't be certain that the R class will line up with what is at SDK build time
        // like in the case of Unity et. al.
        class IdHelper {
            public int id(String identifier) {
                int ret = context.getResources().getIdentifier(identifier, "id", context.getPackageName());
                if (ret == 0) {
                    throw new Resources.NotFoundException("Could not find R.id." + identifier);
                }
                return ret;
            }

            public int layout(String identifier) {
                int ret = context.getResources().getIdentifier(identifier, "layout", context.getPackageName());
                if (ret == 0) {
                    throw new Resources.NotFoundException("Could not find R.layout." + identifier);
                }
                return ret;
            }

            public int integer(String identifier) {
                int ret = context.getResources().getIdentifier(identifier, "integer", context.getPackageName());
                if (ret == 0) {
                    throw new Resources.NotFoundException("Could not find R.integer." + identifier);
                }
                return context.getResources().getInteger(ret);
            }

            public int drawable(String identifier) {
                int ret = context.getResources().getIdentifier(identifier, "drawable", context.getPackageName());
                if (ret == 0) {
                    throw new Resources.NotFoundException("Could not find R.drawable." + identifier);
                }
                return ret;
            }
        }
        final IdHelper R = new IdHelper(); // Declaring local as 'R' ensures we don't accidentally use the other R

        NotificationCompat.Builder builder = getNotificationCompatBuilder(context);

        // Rich text message
        Spanned richMessageText = Html.fromHtml(teakNotificaton.message);

        // Configure notification behavior
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        builder.setOnlyAlertOnce(true);
        builder.setAutoCancel(true);
        builder.setTicker(richMessageText);

        // Icon accent color (added in API 21 version of Notification builder, so use reflection)
        try {
            Method setColor = builder.getClass().getMethod("setColor", int.class);
            if (setColor != null) {
                setColor.invoke(builder, R.integer("io_teak_notification_accent_color"));
            }
        } catch (Exception ignored) {
        }

        // Get app icon
        int tempAppIconResourceId;
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), 0);
            tempAppIconResourceId = ai.icon;
        } catch (Exception e) {
            Teak.log.e("notification_builder", "Unable to load app icon resource for Notification.");
            return null;
        }
        final int appIconResourceId = tempAppIconResourceId;

        // Assign notification icons
        int smallNotificationIcon = appIconResourceId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                smallNotificationIcon = R.drawable("io_teak_small_notification_icon");
            } catch (Exception ignored) {
            }
        }

        Bitmap largeNotificationIcon = null;
        try {
            largeNotificationIcon = BitmapFactory.decodeResource(context.getResources(),
                R.drawable("io_teak_large_notification_icon"));
        } catch (Exception ignored) {
        }

        builder.setSmallIcon(smallNotificationIcon);
        if (largeNotificationIcon != null) {
            builder.setLargeIcon(largeNotificationIcon);
        }

        Random rng = new Random();

        ComponentName cn = new ComponentName(context.getPackageName(), "io.teak.sdk.Teak");

        // Create intent to fire if/when notification is cleared
        Intent pushClearedIntent = new Intent(context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX);
        pushClearedIntent.putExtras(bundle);
        pushClearedIntent.setComponent(cn);
        PendingIntent pushClearedPendingIntent = PendingIntent.getBroadcast(context, rng.nextInt(), pushClearedIntent, PendingIntent.FLAG_ONE_SHOT);
        builder.setDeleteIntent(pushClearedPendingIntent);

        // Create intent to fire if/when notification is opened, attach bundle info
        Intent pushOpenedIntent = new Intent(context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX);
        pushOpenedIntent.putExtras(bundle);
        pushOpenedIntent.setComponent(cn);
        PendingIntent pushOpenedPendingIntent = PendingIntent.getBroadcast(context, rng.nextInt(), pushOpenedIntent, PendingIntent.FLAG_ONE_SHOT);
        builder.setContentIntent(pushOpenedPendingIntent);

        // Notification builder
        Notification nativeNotification = builder.build();
        nativeNotification.flags |= Notification.FLAG_ONLY_ALERT_ONCE; // Only buzz/sound once

        class ViewBuilder {
            private RemoteViews buildViews(String name) throws Exception {
                final int viewLayout = R.layout(name);
                RemoteViews remoteViews = new RemoteViews(
                    context.getPackageName(),
                    viewLayout);

                // To let us query for information about the view
                final LayoutInflater factory = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (factory == null) {
                    throw new Exception("Unable to get LayoutInflater service.");
                }

                // ViewFlipper must inflate on main thread
                FutureTask<View> viewInflaterRunnable = new FutureTask<>(new Callable<View>() {
                    @Override
                    public View call() throws Exception {
                        return factory.inflate(viewLayout, null);
                    }
                });
                new Handler(Looper.getMainLooper()).post(viewInflaterRunnable);
                View inflatedView = viewInflaterRunnable.get();

                // Configure view
                JSONObject viewConfig = teakNotificaton.display.getJSONObject(name);
                Iterator<?> keys = viewConfig.keys();

                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    String value = viewConfig.getString(key);

                    if (value == null || value.length() == 0) continue;

                    int viewElementId = R.id(key);
                    View viewElement = inflatedView.findViewById(viewElementId);

                    //noinspection StatementWithEmptyBody
                    if (isUIType(viewElement, Button.class)) {
                        // Button must go before TextView, because Button is a TextView
                        // TODO: Need more config options for button, image, text, deep link
                    } else if (isUIType(viewElement, TextView.class)) {
                        remoteViews.setTextViewText(viewElementId, Html.fromHtml(value));
                    } else //noinspection StatementWithEmptyBody
                        if (isUIType(viewElement, ImageButton.class)) {
                        // ImageButton must go before ImageView, because ImageButton is a ImageView
                    } else if (isUIType(viewElement, ImageView.class)) {
                        if (value.equalsIgnoreCase("BUILTIN_APP_ICON")) {
                            remoteViews.setImageViewResource(viewElementId, appIconResourceId);
                        } else if (value.equalsIgnoreCase("NONE")) {
                            remoteViews.setViewVisibility(viewElementId, View.GONE);
                        } else {
                            Bitmap bitmap = loadBitmapFromURI(new URI("assets:///777-animated-phone-it-in.png")); //loadBitmapFromURI(new URI(value));
                            if(true) { // HAX - Animated
                                try {
                                    //stream = context.getAssets().open("777-animated-phone-it-in.png");
                                    //stream = context.getAssets().open("777-animated-phone-it-in-snow.png");
                                    final int numCols = 2;
                                    final int numRows = 4;
                                    final int frameWidth = 512;
                                    final int frameHeight = 256;

                                    int frameIdx = 0;
                                    for (int x = 0; x < numCols; x++) {
                                        for (int y = 0; y < numRows; y++) {
                                            final int startX = x * frameWidth;
                                            final int startY = y * frameHeight;
                                            Bitmap frame = Bitmap.createBitmap(bitmap, startX, startY, frameWidth, frameHeight);

                                            int frameViewId = R.id("frame_" + frameIdx);
                                            remoteViews.setImageViewBitmap(frameViewId, frame);

                                            frameIdx++;
                                        }
                                    }
                                } catch (Exception e) {
                                    Teak.log.exception(e);
                                }
                            } else if (bitmap != null) {
                                remoteViews.setImageViewBitmap(viewElementId, bitmap);
                            }
                        }
                    }
                    // TODO: Else, report error to dashboard.
                }

                return remoteViews;
            }

            private Bitmap loadBitmapFromURI(URI bitmapUri) throws Exception {
                Bitmap ret = null;
                try {
                    if (bitmapUri.getScheme().equals("assets")) {
                        String assetFilePath = bitmapUri.getPath();
                        assetFilePath = assetFilePath.startsWith("/") ? assetFilePath.substring(1) : assetFilePath;
                        InputStream is = context.getAssets().open(assetFilePath);
                        ret = BitmapFactory.decodeStream(is);
                        is.close();
                    } else {
                        URL aURL = new URL(bitmapUri.toString());
                        URLConnection conn = aURL.openConnection();
                        conn.connect();
                        InputStream is = conn.getInputStream();
                        BufferedInputStream bis = new BufferedInputStream(is);
                        ret = BitmapFactory.decodeStream(bis);
                        bis.close();
                        is.close();
                    }
                } catch (SSLException ignored) {
                } catch (FileNotFoundException ignored) {
                }
                return ret;
            }

            private boolean isUIType(View viewElement, Class clazz) {
                // TODO: Do more error checking to see if this is an AppCompat* class, and don't use InstanceOf
                return clazz.isInstance(viewElement);
            }
        }
        ViewBuilder viewBuilder = new ViewBuilder();

        // Configure 'contentView'
        nativeNotification.contentView = viewBuilder.buildViews(teakNotificaton.display.getString("contentView"));

        // Check for Jellybean (API 16, 4.1)+ for expanded view
        RemoteViews bigContentView = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && teakNotificaton.display.has("bigContentView")) {
            try {
                bigContentView = viewBuilder.buildViews(teakNotificaton.display.getString("bigContentView"));
            } catch (Exception ignored) {
            }
        }

        // Assign expanded view if it's there, otherwise hide the pulldown view (if it exists)
        if (bigContentView != null) {
            // Use reflection to avoid compile-time issues
            try {
                Field bigContentViewField = nativeNotification.getClass().getField("bigContentView");
                bigContentViewField.set(nativeNotification, bigContentView);
            } catch (Exception ignored) {
            }
        }

        return nativeNotification;
    }

    private static Notification createNativeNotificationV0(final Context context, Bundle bundle, TeakNotification teakNotificaton) {
        NotificationCompat.Builder builder = getNotificationCompatBuilder(context);

        // Rich text message
        Spanned richMessageText = Html.fromHtml(teakNotificaton.message);

        // Configure notification behavior
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        builder.setOnlyAlertOnce(true);
        builder.setAutoCancel(true);
        builder.setTicker(richMessageText);

        // Set small view image
        int smallIconResourceId;
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), 0);
            smallIconResourceId = ai.icon;
            builder.setSmallIcon(smallIconResourceId);
        } catch (Exception e) {
            Teak.log.e("notification_builder", "Unable to load icon resource for Notification.");
            return null;
        }

        Random rng = new Random();

        ComponentName cn = new ComponentName(context.getPackageName(), "io.teak.sdk.Teak");

        // Create intent to fire if/when notification is cleared
        Intent pushClearedIntent = new Intent(context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_CLEARED_INTENT_ACTION_SUFFIX);
        pushClearedIntent.putExtras(bundle);
        pushClearedIntent.setComponent(cn);
        PendingIntent pushClearedPendingIntent = PendingIntent.getBroadcast(context, rng.nextInt(), pushClearedIntent, PendingIntent.FLAG_ONE_SHOT);
        builder.setDeleteIntent(pushClearedPendingIntent);

        // Create intent to fire if/when notification is opened, attach bundle info
        Intent pushOpenedIntent = new Intent(context.getPackageName() + TeakNotification.TEAK_NOTIFICATION_OPENED_INTENT_ACTION_SUFFIX);
        pushOpenedIntent.putExtras(bundle);
        pushOpenedIntent.setComponent(cn);
        PendingIntent pushOpenedPendingIntent = PendingIntent.getBroadcast(context, rng.nextInt(), pushOpenedIntent, PendingIntent.FLAG_ONE_SHOT);
        builder.setContentIntent(pushOpenedPendingIntent);

        // Notification builder
        Notification nativeNotification = builder.build();

        // Because we can't be certain that the R class will line up with what is at SDK build time
        // like in the case of Unity et. al.
        class IdHelper {
            public int id(String identifier) {
                int ret = context.getResources().getIdentifier(identifier, "id", context.getPackageName());
                if (ret == 0) {
                    throw new Resources.NotFoundException("Could not find R.id." + identifier);
                }
                return ret;
            }

            public int layout(String identifier) {
                int ret = context.getResources().getIdentifier(identifier, "layout", context.getPackageName());
                if (ret == 0) {
                    throw new Resources.NotFoundException("Could not find R.layout." + identifier);
                }
                return ret;
            }
        }
        IdHelper R = new IdHelper(); // Declaring local as 'R' ensures we don't accidentally use the other R

        // Configure notification small view
        RemoteViews smallView = new RemoteViews(
            context.getPackageName(),
            R.layout("teak_notif_no_title"));

        // Set small view image
        smallView.setImageViewResource(R.id("left_image"), smallIconResourceId);

        // Set small view text
        smallView.setTextViewText(R.id("text"), richMessageText);
        nativeNotification.contentView = smallView;

        // Check for Jellybean (API 16, 4.1)+ for expanded view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
            teakNotificaton.longText != null &&
            !teakNotificaton.longText.isEmpty()) {
            RemoteViews bigView = new RemoteViews(
                context.getPackageName(),
                R.layout("teak_big_notif_image_text"));

            // Set big view text
            bigView.setTextViewText(R.id("text"), Html.fromHtml(teakNotificaton.longText));

            URI imageAssetA = null;
            try {
                if (teakNotificaton.imageAssetA != null) {
                    imageAssetA = new URI(teakNotificaton.imageAssetA);
                }
            } catch (Exception ignored) {
            }

            Bitmap topImageBitmap = null;
            if (imageAssetA != null) {
                try {
                    URL aURL = new URL(imageAssetA.toString());
                    URLConnection conn = aURL.openConnection();
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    BufferedInputStream bis = new BufferedInputStream(is);
                    topImageBitmap = BitmapFactory.decodeStream(bis);
                    bis.close();
                    is.close();
                } catch (Exception ignored) {
                }
            }

            if (topImageBitmap == null) {
                try {
                    InputStream istr = context.getAssets().open("teak_notif_large_image_default.png");
                    topImageBitmap = BitmapFactory.decodeStream(istr);
                } catch (Exception ignored) {
                }
            }

            if (topImageBitmap != null) {
                // Set large bitmap
                bigView.setImageViewBitmap(R.id("top_image"), topImageBitmap);

                // Use reflection to avoid compile-time issues, we check minimum API version above
                try {
                    Field bigContentViewField = nativeNotification.getClass().getField("bigContentView");
                    bigContentViewField.set(nativeNotification, bigView);
                } catch (Exception ignored) {
                }
            } else {
                Teak.log.e("notification_builder", "Unable to load image asset for Notification.");
                // Hide pulldown
                smallView.setViewVisibility(R.id("pulldown_layout"), View.INVISIBLE);
            }
        } else {
            // Hide pulldown
            smallView.setViewVisibility(R.id("pulldown_layout"), View.INVISIBLE);
        }

        return nativeNotification;
    }
}
