package io.teak.sdk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.teak.sdk.json.JSONArray;
import io.teak.sdk.json.JSONException;
import io.teak.sdk.json.JSONObject;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Helpers {
    @SuppressWarnings("deprecation")
    @SuppressLint("PackageManagerGetSignatures")
    public static Signature[] getAppSignatures(@NonNull Context appContext) {
        try {
            return appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean stringsAreEqual(final @Nullable String a, final @Nullable String b) {
        if (a == b) return true;

        final String notNullString = (a == null ? b : a);
        final String possiblyNullString = (a == null ? a : b);
        return notNullString.equals(possiblyNullString);
    }

    public static String getStringOrNullFromIntentExtra(final @NonNull Intent intent, final @NonNull String key) {
        String ret = null;
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            String value = bundle.getString(key);
            if (value != null && !value.isEmpty()) {
                ret = value;
            }
        }
        return ret;
    }

    public static boolean getBooleanFromBundle(Bundle b, String key) {
        String boolAsStringMaybe = b.getString(key);
        if (boolAsStringMaybe != null) {
            return Boolean.parseBoolean(boolAsStringMaybe);
        }
        return b.getBoolean(key);
    }

    public static boolean isAmazonDevice(final @NonNull Context context) {
        final String bundleId = context.getPackageName();
        final PackageManager packageManager = context.getPackageManager();
        final String installerPackage = packageManager == null ? null : packageManager.getInstallerPackageName(bundleId);
        return "amazon".equalsIgnoreCase(Build.MANUFACTURER) ||
            "com.amazon.venezia".equals(installerPackage);
    }

    public static class mm {
        public static Map<String, Object> h(@NonNull Object... args) {
            Map<String, Object> ret = new HashMap<>();
            if (args.length % 2 != 0)
                throw new InvalidParameterException("Args must be in key value pairs.");
            for (int i = 0; i < args.length; i += 2) {
                ret.put(args[i].toString(), args[i + 1]);
            }
            return ret;
        }
    }

    public static String join(CharSequence delimiter, Iterable tokens) {
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = tokens.iterator();
        if (it.hasNext()) {
            sb.append(it.next());
            while (it.hasNext()) {
                sb.append(delimiter);
                sb.append(it.next());
            }
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token : tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(token);
        }
        return sb.toString();
    }

    public static boolean isNullOrEmpty(final @Nullable String string) {
        return string == null || string.trim().isEmpty();
    }

    public static boolean is_equal(final @Nullable Object a, final @Nullable Object b) {
        return (a == b) ||
            (a != null && a.equals(b)) ||
            (b != null && b.equals(a));
    }

    public static JSONObject bundleToJson(Bundle bundle) {
        JSONObject json = new JSONObject();
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            try {
                json.put(key, JSONObject.wrap(bundle.get(key)));
            } catch (JSONException ignored) {
            }
        }
        return json;
    }

    public static Bundle jsonToGCMBundle(JSONObject jsonObject) {
        Bundle bundle = new Bundle();

        for (Iterator<String> it = jsonObject.keys(); it.hasNext();) {
            String key = it.next();
            Object obj = jsonObject.get(key);

            if (obj instanceof Integer || obj instanceof Long) {
                if (key.startsWith("google.")) {
                    long value = ((Number) obj).longValue();
                    bundle.putLong(key, value);
                } else {
                    bundle.putString(key, String.valueOf(obj));
                }
            } else if (obj instanceof Boolean) {
                boolean value = (Boolean) obj;
                bundle.putBoolean(key, value);
            } else if (obj instanceof Float || obj instanceof Double) {
                if (key.startsWith("google.")) {
                    double value = ((Number) obj).doubleValue();
                    bundle.putDouble(key, value);
                } else {
                    bundle.putString(key, String.valueOf(obj));
                }
            } else if (obj instanceof JSONObject || obj instanceof JSONArray) {
                String value = obj.toString();
                bundle.putString(key, value);
            } else if (obj instanceof String) {
                String value = jsonObject.getString(key);
                bundle.putString(key, value);
            }
        }

        return bundle;
    }

    private static int targetSDKVersion = 0;
    public static int getTargetSDKVersion(@NonNull Context context) {
        if (targetSDKVersion == 0) {
            try {
                ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
                targetSDKVersion = appInfo.targetSdkVersion;
            } catch (Exception ignored) {
            }
        }
        return targetSDKVersion;
    }

    public static String formatSig(Signature sig, String hashType) throws java.security.NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(hashType);
        byte[] sha256Bytes = md.digest(sig.toByteArray());
        StringBuilder hexString = new StringBuilder();
        for (byte aSha256Byte : sha256Bytes) {
            if (hexString.length() > 0) {
                hexString.append(":");
            }

            if ((0xff & aSha256Byte) < 0x10) {
                hexString.append("0").append(Integer.toHexString((0xFF & aSha256Byte)));
            } else {
                hexString.append(Integer.toHexString(0xFF & aSha256Byte));
            }
        }
        return hexString.toString().toUpperCase();
    }
}
