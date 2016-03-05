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

import java.lang.reflect.*;

import android.os.Bundle;

import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.support.v4.content.LocalBroadcastManager;

import android.util.Log;

class FacebookAccessTokenBroadcast {
    LocalBroadcastManager broadcastManager;

    Method com_facebook_Session_getActiveSession;
    Method com_facebook_Session_getAccessToken;
    Method com_facebook_AccessToken_getToken;

    String facebook_3_x_BroadcastAction;
    String facebook_4_x_BroadcastAction;

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(Teak.isDebug) {
                Log.d(Teak.LOG_TAG, "Facebook Broadcast received: " + action);
            }

            String accessTokenString = null;
            if(facebook_3_x_BroadcastAction.equals(action)) {
                try {
                    Object session = com_facebook_Session_getActiveSession.invoke(null);
                    accessTokenString = (String)com_facebook_Session_getAccessToken.invoke(session);
                } catch(Exception e) {
                    Log.e(Teak.LOG_TAG, "Failed to get Facebook Access Token, error: " + Log.getStackTraceString(e));
                }
            } else if(facebook_4_x_BroadcastAction.equals(action)) {
                Object accessToken = intent.getParcelableExtra(FACEBOOK_4_x_NEW_ACCESS_TOKEN_KEY);

                try {
                    accessTokenString = (String)com_facebook_AccessToken_getToken.invoke(accessToken);
                } catch(Exception e) {
                    Log.e(Teak.LOG_TAG, "Failed to get Facebook Access Token, error: " + Log.getStackTraceString(e));
                }
            }

            if(accessTokenString != null) {
                if(Teak.isDebug) {
                    Log.d(Teak.LOG_TAG, "Facebook Access Token: " + accessTokenString);
                }
            }
        }
    };

    private static final String FACEBOOK_SDK_VERSION_CLASS_NAME = "com.facebook.FacebookSdkVersion";

    private static final String FACEBOOK_3_x_SESSION_CLASS_NAME = "com.facebook.Session";
    private static final String FACEBOOK_3_x_BROADCAST_ACTION_FIELD = "ACTION_ACTIVE_SESSION_OPENED";

    private static final String FACEBOOK_4_x_ACCESS_TOKEN_CLASS_NAME = "com.facebook.AccessToken";
    private static final String FACEBOOK_4_x_ACCESS_TOKEN_MANAGER_CLASS_NAME = "com.facebook.AccessTokenManaer";
    private static final String FACEBOOK_4_x_BROADCAST_ACTION_FIELD = "ACTION_CURRENT_ACCESS_TOKEN_CHANGED";
    private static final String FACEBOOK_4_x_NEW_ACCESS_TOKEN_KEY = "com.facebook.sdk.EXTRA_NEW_ACCESS_TOKEN";

    public FacebookAccessTokenBroadcast(Context context) {
        this.broadcastManager = LocalBroadcastManager.getInstance(context);

        // Get the Facebook SDK Version string
        Class<?> com_facebook_FacebookSdkVersion = null;
        try {
            com_facebook_FacebookSdkVersion = Class.forName(FACEBOOK_SDK_VERSION_CLASS_NAME);
        } catch(ClassNotFoundException e) {
            Log.e(Teak.LOG_TAG, "Facebook SDK not found.");
        }

        Field fbSdkVersionField = null;
        if(com_facebook_FacebookSdkVersion != null) {
            try {
                fbSdkVersionField = com_facebook_FacebookSdkVersion.getDeclaredField("BUILD");
            } catch(NoSuchFieldException e) {
                Log.e(Teak.LOG_TAG, "Facebook SDK version field not found.");
            }
        }

        String fbSdkVersion = null;
        if(fbSdkVersionField != null) {
            try {
                fbSdkVersion = fbSdkVersionField.get(null).toString();
                if(Teak.isDebug) {
                    Log.d(Teak.LOG_TAG, "Facebook SDK version string: " + fbSdkVersion);
                }
            } catch(Exception e) {
                Log.e(Teak.LOG_TAG, e.toString());
            }
        }

        if(fbSdkVersion != null) {
            String[] versionStrings = fbSdkVersion.split("\\.");
            int[] versionInts = new int[versionStrings.length];
            for(int i = 0; i < versionStrings.length; i++) {
                versionInts[i] = Integer.parseInt(versionStrings[i]);
            }

            switch(versionInts[0]) {
                case 3: {
                    if(Teak.isDebug) {
                        Log.d(Teak.LOG_TAG, "Listening for Facebook SDK 3.x broadcast actions.");
                    }

                    Class<?> com_facebook_Session = null;
                    try {
                        com_facebook_Session = Class.forName(FACEBOOK_3_x_SESSION_CLASS_NAME);
                        if(Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "Found " + com_facebook_Session.toString());
                        }

                        this.com_facebook_Session_getActiveSession = com_facebook_Session.getMethod("getActiveSession");
                        try {
                            this.com_facebook_Session_getAccessToken = com_facebook_Session.getMethod("getAccessToken");
                        } catch(NoSuchMethodException e) {
                            Log.e(Teak.LOG_TAG, FACEBOOK_3_x_SESSION_CLASS_NAME + ".getAccessToken() not found: " + Log.getStackTraceString(e));
                        }

                        Field f = com_facebook_Session.getDeclaredField(FACEBOOK_3_x_BROADCAST_ACTION_FIELD);
                        this.facebook_3_x_BroadcastAction = (String)f.get(null);
                        if(Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "Found broadcast action: " + this.facebook_3_x_BroadcastAction);
                        }
                    } catch(ClassNotFoundException e) {
                        Log.e(Teak.LOG_TAG, FACEBOOK_3_x_SESSION_CLASS_NAME + " not found.");
                    } catch(NoSuchMethodException e) {
                        Log.e(Teak.LOG_TAG, FACEBOOK_3_x_SESSION_CLASS_NAME + ".getActiveSession() not found: " + Log.getStackTraceString(e));
                    } catch(Exception e) {
                        Log.e(Teak.LOG_TAG, "Error occured working with reflected Facebook SDK: " + Log.getStackTraceString(e));
                    }

                    if(this.com_facebook_Session_getActiveSession != null &&
                       this.com_facebook_Session_getAccessToken != null &&
                       this.facebook_3_x_BroadcastAction != null) {
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(this.facebook_3_x_BroadcastAction);

                        this.broadcastManager.registerReceiver(this.broadcastReceiver, filter);
                    }
                }
                break;

                case 4: {
                    Class<?> com_facebook_AccessToken = null;
                    try {
                        com_facebook_AccessToken = Class.forName(FACEBOOK_4_x_ACCESS_TOKEN_CLASS_NAME);
                        if(Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "Found " + com_facebook_AccessToken.toString());
                        }

                        this.com_facebook_AccessToken_getToken = com_facebook_AccessToken.getMethod("getToken");
                    } catch(ClassNotFoundException e) {
                        Log.e(Teak.LOG_TAG, FACEBOOK_4_x_ACCESS_TOKEN_CLASS_NAME + " not found.");
                    } catch(NoSuchMethodException e) {
                        Log.e(Teak.LOG_TAG, FACEBOOK_4_x_ACCESS_TOKEN_CLASS_NAME + ".getToken() not found: " + Log.getStackTraceString(e));
                    } catch(Exception e) {
                        Log.e(Teak.LOG_TAG, "Error occured working with reflected Facebook SDK: " + Log.getStackTraceString(e));
                    }

                    try {
                        Class<?> com_facebook_AccessTokenManager = null;
                        com_facebook_AccessTokenManager = Class.forName(FACEBOOK_4_x_ACCESS_TOKEN_MANAGER_CLASS_NAME);
                        if(Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "Found " + com_facebook_AccessTokenManager.toString());
                        }

                        Field f = com_facebook_AccessTokenManager.getDeclaredField(FACEBOOK_4_x_BROADCAST_ACTION_FIELD);
                        this.facebook_4_x_BroadcastAction = (String)f.get(null);
                        if(Teak.isDebug) {
                            Log.d(Teak.LOG_TAG, "Found broadcast action: " + this.facebook_4_x_BroadcastAction);
                        }
                    } catch(ClassNotFoundException e) {
                        Log.e(Teak.LOG_TAG, FACEBOOK_4_x_ACCESS_TOKEN_MANAGER_CLASS_NAME + " not found.");
                    } catch(Exception e) {
                        Log.e(Teak.LOG_TAG, "Error occured working with reflected Facebook SDK: " + Log.getStackTraceString(e));
                    }

                    if(this.com_facebook_AccessToken_getToken != null &&
                       this.facebook_4_x_BroadcastAction != null) {
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(this.facebook_4_x_BroadcastAction);

                        this.broadcastManager.registerReceiver(this.broadcastReceiver, filter);
                    }
                }
                break;

                default: {
                    Log.e(Teak.LOG_TAG, "Don't know how to use Facebook SDK version " + versionInts[0]);
                }
            }
        }
    }

    void unregister(Context context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(this.broadcastReceiver);
    }
}
