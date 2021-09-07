package io.teak.sdk.core;

import android.content.Intent;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;

import androidx.annotation.NonNull;
import io.teak.sdk.Helpers;
import io.teak.sdk.Teak;
import io.teak.sdk.TeakConfiguration;
import io.teak.sdk.json.JSONObject;
import io.teak.sdk.referrer.InstallReferrerFuture;

public class LaunchDataSource implements Future<Teak.LaunchData> {
    public static final LaunchDataSource Unattributed = new LaunchDataSource(Helpers.futureForValue(Teak.LaunchData.Unattributed));

    public boolean isProcessed = false;
    private final Future<Teak.LaunchData> launchDataFuture;

    private LaunchDataSource(@NonNull final Future<Teak.LaunchData> launchDataFuture) {
        this.launchDataFuture = launchDataFuture;
    }

    public static LaunchDataSource sourceWithUpdatedDeepLink(@NonNull final Teak.LaunchData launchData, @NonNull final Uri deepLink) {
        return new LaunchDataSource(Helpers.futureForValue(launchData.mergeDeepLink(deepLink)));
    }

    public static LaunchDataSource sourceFromIntent(@NonNull final Intent intent) {
        // If is is not a "first launch" then we can take the easy path of not needing to wait for
        // the possibility of an install referrer.
        final boolean isFirstLaunch = intent.getBooleanExtra("teakIsFirstLaunch", false);

        // So the fast path is no first launch, and teakNotifId in the Intent extras.
        if (!isFirstLaunch) {
            // If it is not a first launch, and there is a teakNotifId, then we do not need to wait for
            // the resolution of a link.
            final String teakNotifId = Helpers.getStringOrNullFromIntentExtra(intent, "teakNotifId");

            if (teakNotifId != null) {
                // This is the fast and easy path. There is a teakNotifId, meaning we launched via a push
                // notification, and it's not the first launch, so we do not need to worry about any
                // install referrer.
                return new LaunchDataSource(Helpers.futureForValue(new Teak.NotificationLaunchData(intent.getExtras())));
            } else {
                // This is not the first launch, but we will need to wait for a link resolution
                final String intentDataString = intent.getDataString();

                // If there's no launch url, this is a completely unattributed launch
                if (Helpers.isNullOrEmpty(intentDataString)) {
                    return LaunchDataSource.Unattributed;
                } else {
                    return new LaunchDataSource(LaunchDataSource.futureFromLinkResolution(Helpers.futureForValue(intentDataString)));
                }
            }
        } else {
            // This is the first launch, which means we need to check for an install referrer.
            //
            // The install referrer may be something like a link from an email, which means that it
            // could also contain a teak_notif_id.
            //
            // However, there cannot also be a teakNotifId from a push notification, because it's the
            // first launch, and Teak therefor cannot possibly know about the device.
            final TeakConfiguration teakConfiguration = TeakConfiguration.get();
            final Future<String> installReferrer = InstallReferrerFuture.get(teakConfiguration.appConfiguration.applicationContext);
            return new LaunchDataSource(LaunchDataSource.futureFromLinkResolution(installReferrer));
        }
    }

    ////// Create a Future which will contain AttributionData from a resolved link

    protected static Future<Teak.LaunchData> futureFromLinkResolution(@NonNull final Future<String> futureForUriOrNull) {
        final TeakConfiguration teakConfiguration = TeakConfiguration.get();

        final FutureTask<Teak.LaunchData> returnTask = new FutureTask<Teak.LaunchData>(() -> {
            Uri httpsUri = null;

            // Wait on the incoming Future
            Uri uri = null;
            try {
                uri = Uri.parse(futureForUriOrNull.get(5, TimeUnit.SECONDS));
            } catch (Exception ignored) {
            }

            if (uri == null) {
                return null;
            }

            // Try and resolve any Teak links
            if (uri.getScheme() != null && (uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                HttpsURLConnection connection = null;
                try {
                    httpsUri = uri.buildUpon().scheme("https").build();
                    URL url = new URL(httpsUri.toString());

                    Teak.log.i("deep_link.request.send", url.toString());

                    connection = (HttpsURLConnection) url.openConnection();
                    connection.setUseCaches(false);
                    connection.setRequestProperty("Accept-Charset", "UTF-8");
                    connection.setRequestProperty("X-Teak-DeviceType", "API");
                    connection.setRequestProperty("X-Teak-Supports-Templates", "TRUE");

                    // Get Response
                    InputStream is;
                    if (connection.getResponseCode() < 400) {
                        is = connection.getInputStream();
                    } else {
                        is = connection.getErrorStream();
                    }
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = rd.readLine()) != null) {
                        response.append(line);
                        response.append('\r');
                    }
                    rd.close();

                    Teak.log.i("deep_link.request.reply", response.toString());

                    try {
                        JSONObject teakData = new JSONObject(response.toString());
                        if (teakData.getString("AndroidPath") != null) {
                            final String androidPath = teakData.getString("AndroidPath");
                            final Pattern pattern = Pattern.compile("^[a-zA-Z0-9+.\\-_]*:");
                            final Matcher matcher = pattern.matcher(androidPath);
                            if (matcher.find()) {
                                uri = Uri.parse(androidPath);
                            } else {
                                uri = Uri.parse(String.format(Locale.US, "teak%s://%s", teakConfiguration.appConfiguration.appId, androidPath));
                            }
                        } else {
                            // Clear httpsUri, so that it won't get sent along to the AttributionData constructor
                            httpsUri = null;
                        }

                        Teak.log.i("deep_link.request.resolve", uri.toString());
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }
                } catch (SSLProtocolException ssl_e) {
                    // Ignored, Sentry issue 'TEAK-SDK-Z'
                } catch (SSLException ssl_e) {
                    // Ignored
                } catch (Exception e) {
                    Teak.log.exception(e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }

            // If this is a link from a Teak email, then it's a notification launch data
            if (Teak.NotificationLaunchData.isTeakEmailUri(uri)) {
                return new Teak.NotificationLaunchData(uri);
            } else {
                return new Teak.RewardlinkLaunchData(uri, httpsUri);
            }
        });

        // Start it running, and return the Future
        ThreadFactory.autoStart(returnTask);
        return returnTask;
    }

    ////// implements Future

    @Override
    public boolean cancel(boolean b) {
        return this.launchDataFuture.cancel(b);
    }

    @Override
    public boolean isCancelled() {
        return this.launchDataFuture.isCancelled();
    }

    @Override
    public boolean isDone() {
        return this.launchDataFuture.isDone();
    }

    @Override
    public Teak.LaunchData get() throws ExecutionException, InterruptedException {
        return this.launchDataFuture.get();
    }

    @Override
    public Teak.LaunchData get(long l, TimeUnit timeUnit) throws ExecutionException, InterruptedException, TimeoutException {
        return this.launchDataFuture.get(l, timeUnit);
    }
}
