/**
 * Copyright 2012 Facebook
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.CarrotInc.Carrot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.facebook.FacebookResource;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.Session.NewPermissionsRequest;

import java.util.*;

public class CarrotLoginActivity extends Activity {
    private Session.StatusCallback statusCallback = new SessionStatusCallback();
    private static final List<String> PERMISSIONS = Arrays.asList("publish_actions");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Session session = Session.getActiveSession();
        if (session == null) {
            Log.d(Carrot.LOG_TAG, "Session was null...");
            if (savedInstanceState != null) {
                session = Session.restoreSession(this, null, statusCallback, savedInstanceState);
                Log.d(Carrot.LOG_TAG, "Attempting to restore Session...");
            }
            if (session == null) {
                session = new Session(this);
                Log.d(Carrot.LOG_TAG, "Creating Session...");
            }
            Session.setActiveSession(session);
            if (session.getState().equals(SessionState.CREATED_TOKEN_LOADED)) {
                Log.d(Carrot.LOG_TAG, "Opening Session for read (1)...");
                session.openForRead(new Session.OpenRequest(this).setCallback(statusCallback));
            }
        }

        if (!session.isOpened() && !session.isClosed()) {
            Log.d(Carrot.LOG_TAG, "Opening Session for read (2)...");
            session.openForRead(new Session.OpenRequest(this).setCallback(statusCallback));
        } else {
            Bundle b = getIntent().getExtras();
            Log.d(Carrot.LOG_TAG, "Opening active Session, allow login UI: " + b.getBoolean("allowLoginUI") + "...");
            Session.openActiveSession(this, b.getBoolean("allowLoginUI"), statusCallback);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Session.getActiveSession().addCallback(statusCallback);
    }

    @Override
    public void onStop() {
        super.onStop();
        Session.getActiveSession().removeCallback(statusCallback);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Session session = Session.getActiveSession();
        Session.saveSession(session, outState);
    }

    private class SessionStatusCallback implements Session.StatusCallback {
        @Override
        public void call(Session session, SessionState state, Exception exception) {
            Log.d(Carrot.LOG_TAG, String.format("New Facebook session state: %s", state.toString()));

            if(Carrot.getActiveInstance() != null) {
                Carrot.getActiveInstance().setAccessToken(session.getAccessToken());
            }

            Bundle b = getIntent().getExtras();
            int authType = b.getInt("authType");

            List<String> permissions = session.getPermissions();
            if(authType == 1 && state.isOpened() && !permissions.containsAll(PERMISSIONS)) {
                if(b.getBoolean("allowLoginUI")) {
                    Session.NewPermissionsRequest newPermissionsRequest = new Session.NewPermissionsRequest(CarrotLoginActivity.this, PERMISSIONS);
                    session.requestNewPublishPermissions(newPermissionsRequest);
                }
            }
            else if(state.isClosed() || state.isOpened()) {
                finish();
            }
        }
    }
}
