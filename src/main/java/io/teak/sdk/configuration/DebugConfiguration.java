package io.teak.sdk.configuration;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.support.annotation.NonNull;

import io.teak.sdk.Teak;

public class DebugConfiguration {
    private static final String PREFERENCE_LOG_LOCAL = "io.teak.sdk.Preferences.LogLocal";
    private static final String PREFERENCE_LOG_REMOTE = "io.teak.sdk.Preferences.LogRemote";

    private final SharedPreferences preferences;

    private boolean logLocal;
    private boolean logRemote;
    private final boolean isDevelopmentBuild;

    public DebugConfiguration(@NonNull Context context) {
        SharedPreferences tempPreferences = null;
        try {
            tempPreferences = context.getSharedPreferences(Teak.PREFERENCES_FILE, Context.MODE_PRIVATE);
        } catch (Exception e) {
            Teak.log.exception(e);
        } finally {
            this.preferences = tempPreferences;
        }

        if (this.preferences == null) {
            this.logLocal = this.logRemote = Teak.forceDebug;
        } else {
            this.logLocal = Teak.forceDebug || this.preferences.getBoolean(PREFERENCE_LOG_LOCAL, false);
            this.logRemote = Teak.forceDebug || this.preferences.getBoolean(PREFERENCE_LOG_REMOTE, false);
        }

        boolean tempDevelopmentBuild = false;
        try {
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            tempDevelopmentBuild = (applicationInfo != null && (0 != (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE)));
        } catch (Exception ignored) {
        }
        this.isDevelopmentBuild = tempDevelopmentBuild;

        // TODO: This should be listener based

        // Set up Logs
        Teak.log.setLoggingEnabled(this.logLocal || this.isDevelopmentBuild, this.logLocal || this.isDevelopmentBuild);
        Teak.log.useRapidIngestionEndpoint(this.isDevelopmentBuild);
    }

    public void setLogPreferences(boolean logLocal, boolean logRemote) {
        if (logLocal != this.logLocal) {
            try {
                synchronized (Teak.PREFERENCES_FILE) {
                    SharedPreferences.Editor editor = this.preferences.edit();
                    editor.putBoolean(PREFERENCE_LOG_LOCAL, logLocal);
                    editor.putBoolean(PREFERENCE_LOG_REMOTE, logRemote);
                    editor.apply();
                }
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        }
        this.logLocal = logLocal;
        this.logRemote = logRemote;

        Teak.log.setLoggingEnabled(this.logLocal || this.isDevelopmentBuild, this.logRemote || this.isDevelopmentBuild);
    }

    public boolean isDebug() {
        return this.isDevelopmentBuild;
    }
}
