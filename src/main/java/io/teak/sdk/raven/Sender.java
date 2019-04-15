package io.teak.sdk.raven;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

import io.teak.sdk.json.JSONObject;

public class Sender implements Callable<Boolean> {
    public static final int SENTRY_VERSION = 7;
    public static final String TEAK_SENTRY_VERSION = "1.1.0";
    public static final String SENTRY_CLIENT = "teak-android/" + TEAK_SENTRY_VERSION;

    private final URL endpoint;
    private final String payload;
    private final String SENTRY_KEY;
    private final String SENTRY_SECRET;
    private final long timestamp;

    public static final String ENDPOINT_KEY = "endpoint";
    public static final String PAYLOAD_KEY = "payload";
    public static final String SENTRY_KEY_KEY = "SENTRY_KEY";
    public static final String SENTRY_SECRET_KEY = "SENTRY_SECRET";
    public static final String TIMESTAMP_KEY = "timestamp";

    public Sender(@NonNull Bundle bundle) throws MalformedURLException {
        this.endpoint = new URL(bundle.getString(ENDPOINT_KEY));
        this.payload = bundle.getString(PAYLOAD_KEY);
        this.SENTRY_KEY = bundle.getString(SENTRY_KEY_KEY);
        this.SENTRY_SECRET = bundle.getString(SENTRY_SECRET_KEY);
        this.timestamp = bundle.getLong(TIMESTAMP_KEY, new Date().getTime() / 1000L);
    }

    @Override
    public Boolean call() throws Exception {
        if (this.payload == null || this.endpoint == null) {
            throw new Exception("");
        }

        HttpsURLConnection connection = null;
        BufferedReader rd = null;

        try {
            connection = (HttpsURLConnection) endpoint.openConnection();
            connection.setRequestProperty("Accept-Charset", "UTF-8");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Encoding", "gzip");
            connection.setRequestProperty("User-Agent", SENTRY_CLIENT);
            connection.setRequestProperty("X-Sentry-Auth",
                String.format(Locale.US, "Sentry sentry_version=%d,sentry_timestamp=%d,sentry_key=%s,sentry_secret=%s,sentry_client=%s",
                    SENTRY_VERSION, this.timestamp, SENTRY_KEY, SENTRY_SECRET, SENTRY_CLIENT));

            GZIPOutputStream wr = new GZIPOutputStream(connection.getOutputStream());
            wr.write(this.payload.getBytes());
            wr.flush();
            wr.close();

            InputStream is;
            if (connection.getResponseCode() < 400) {
                is = connection.getInputStream();
            } else {
                is = connection.getErrorStream();
            }
            rd = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }

            try {
                // TODO: If this was throttled etc, return false and it will be retried
                JSONObject jsonResponse = new JSONObject(response.toString());
                if (jsonResponse.getBoolean("foo")) {
                    return false;
                }
            } catch (Exception ignored) {
            }

            return true;
        } finally {
            if (rd != null) {
                try {
                    rd.close();
                } catch (Exception ignored) {
                }
            }

            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
