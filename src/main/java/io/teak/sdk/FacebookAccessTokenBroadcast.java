package io.teak.sdk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import io.teak.sdk.event.FacebookAccessTokenEvent;
import io.teak.sdk.support.ILocalBroadcastManager;
import java.lang.reflect.*;

public class FacebookAccessTokenBroadcast {
    private ILocalBroadcastManager broadcastManager;

    private Method com_facebook_Session_getActiveSession;
    private Method com_facebook_Session_getAccessToken;
    private Method com_facebook_AccessToken_getToken;

    private String facebook_3_x_BroadcastAction;
    private String facebook_4_x_BroadcastAction;

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            String accessTokenString = null;
            if (facebook_3_x_BroadcastAction != null && facebook_3_x_BroadcastAction.equals(action)) {
                try {
                    Object session = com_facebook_Session_getActiveSession.invoke(null);
                    accessTokenString = (String) com_facebook_Session_getAccessToken.invoke(session);
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
            } else if (facebook_4_x_BroadcastAction != null && facebook_4_x_BroadcastAction.equals(action)) {
                Object accessToken = intent.getParcelableExtra(FACEBOOK_4_x_NEW_ACCESS_TOKEN_KEY);

                if (accessToken != null) {
                    try {
                        accessTokenString = (String) com_facebook_AccessToken_getToken.invoke(accessToken);
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }
                }
            }

            if (accessTokenString != null) {
                TeakEvent.postEvent(new FacebookAccessTokenEvent(accessTokenString));
            }
        }
    };

    private static final String FACEBOOK_SDK_VERSION_CLASS_NAME = "com.facebook.FacebookSdkVersion";

    private static final String FACEBOOK_3_x_SESSION_CLASS_NAME = "com.facebook.Session";
    private static final String FACEBOOK_3_x_BROADCAST_ACTION_FIELD = "ACTION_ACTIVE_SESSION_OPENED";

    private static final String FACEBOOK_4_x_ACCESS_TOKEN_CLASS_NAME = "com.facebook.AccessToken";
    private static final String FACEBOOK_4_x_ACCESS_TOKEN_MANAGER_CLASS_NAME = "com.facebook.AccessTokenManager";
    private static final String FACEBOOK_4_x_BROADCAST_ACTION_FIELD = "ACTION_CURRENT_ACCESS_TOKEN_CHANGED";
    private static final String FACEBOOK_4_x_NEW_ACCESS_TOKEN_KEY = "com.facebook.sdk.EXTRA_NEW_ACCESS_TOKEN";

    FacebookAccessTokenBroadcast(Context context) {
        this.broadcastManager = DefaultObjectFactory.createLocalBroadcastManager(context);

        // Get the Facebook SDK Version string
        Class<?> com_facebook_FacebookSdkVersion = null;
        try {
            com_facebook_FacebookSdkVersion = Class.forName(FACEBOOK_SDK_VERSION_CLASS_NAME);
        } catch (ClassNotFoundException ignored) {
            Teak.log.e("facebook", FACEBOOK_SDK_VERSION_CLASS_NAME + " not found. Facebook SDK hooks disabled.");
        } catch (Exception e) {
            Teak.log.exception(e);
        }

        Field fbSdkVersionField = null;
        if (com_facebook_FacebookSdkVersion != null) {
            try {
                fbSdkVersionField = com_facebook_FacebookSdkVersion.getDeclaredField("BUILD");
            } catch (NoSuchFieldException e) {
                Teak.log.exception(e);
            }
        }

        String fbSdkVersion = null;
        if (fbSdkVersionField != null) {
            try {
                fbSdkVersion = fbSdkVersionField.get(null).toString();
                Teak.log.i("facebook", Helpers.mm.h("version", fbSdkVersion));
            } catch (Exception e) {
                Teak.log.exception(e);
            }
        }

        if (fbSdkVersion != null) {
            String[] fbSDKs = fbSdkVersion.split("/");
            String[] versionStrings = fbSDKs[0].split("\\.");
            int[] versionInts = new int[versionStrings.length];
            for (int i = 0; i < versionStrings.length; i++) {
                versionInts[i] = Integer.parseInt(versionStrings[i]);
            }

            switch (versionInts[0]) {
                case 3: {
                    Class<?> com_facebook_Session;
                    try {
                        com_facebook_Session = Class.forName(FACEBOOK_3_x_SESSION_CLASS_NAME);

                        this.com_facebook_Session_getActiveSession = com_facebook_Session.getMethod("getActiveSession");
                        try {
                            this.com_facebook_Session_getAccessToken = com_facebook_Session.getMethod("getAccessToken");
                        } catch (NoSuchMethodException e) {
                            Teak.log.exception(e);
                        }

                        Field f = com_facebook_Session.getDeclaredField(FACEBOOK_3_x_BROADCAST_ACTION_FIELD);
                        this.facebook_3_x_BroadcastAction = (String) f.get(null);
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }

                    if (this.com_facebook_Session_getActiveSession != null &&
                        this.com_facebook_Session_getAccessToken != null &&
                        this.facebook_3_x_BroadcastAction != null) {
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(this.facebook_3_x_BroadcastAction);

                        this.broadcastManager.registerReceiver(this.broadcastReceiver, filter);
                    }
                } break;

                // Facebook SDK versions 4.x, 5.x, and 6.x use the same methods
                case 4:
                case 5:
                case 6: {
                    Class<?> com_facebook_AccessToken;
                    try {
                        com_facebook_AccessToken = Class.forName(FACEBOOK_4_x_ACCESS_TOKEN_CLASS_NAME);
                        this.com_facebook_AccessToken_getToken = com_facebook_AccessToken.getMethod("getToken");
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }

                    try {
                        Class<?> com_facebook_AccessTokenManager;
                        com_facebook_AccessTokenManager = Class.forName(FACEBOOK_4_x_ACCESS_TOKEN_MANAGER_CLASS_NAME);

                        Field f = com_facebook_AccessTokenManager.getDeclaredField(FACEBOOK_4_x_BROADCAST_ACTION_FIELD);
                        f.setAccessible(true);
                        this.facebook_4_x_BroadcastAction = (String) f.get(null);
                    } catch (Exception e) {
                        Teak.log.exception(e);
                    }

                    if (this.com_facebook_AccessToken_getToken != null &&
                        this.facebook_4_x_BroadcastAction != null) {
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(this.facebook_4_x_BroadcastAction);

                        this.broadcastManager.registerReceiver(this.broadcastReceiver, filter);
                    }
                } break;
                default: {
                    Teak.log.e("facebook", "Don't know how to use Facebook SDK version " + versionInts[0]);
                } break;
            }
        }
    }

    void unregister() {
        if (this.broadcastManager != null) {
            this.broadcastManager.unregisterReceiver(this.broadcastReceiver);
        }
    }

    public static String getCurrentAccessToken() {
        try {
            Class<?> com_facebook_AccessToken = Class.forName(FACEBOOK_4_x_ACCESS_TOKEN_CLASS_NAME);
            Method com_facebook_AccessToken_getCurrentAccessToken = com_facebook_AccessToken.getMethod("getCurrentAccessToken");
            Method com_facebook_AccessToken_getToken = com_facebook_AccessToken.getMethod("getToken");

            Object currentAccessToken = com_facebook_AccessToken_getCurrentAccessToken.invoke(null);
            if (currentAccessToken != null) {
                Object accessTokenString = com_facebook_AccessToken_getToken.invoke(currentAccessToken);
                if (accessTokenString != null) {
                    return accessTokenString.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
