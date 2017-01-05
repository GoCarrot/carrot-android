package com.teakio.pushtest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import io.teak.sdk.Teak;
import io.teak.sdk.TeakNotification;

import android.view.View;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    public static final String LOG_TAG = "Teak:Example";

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TeakNotification.LAUNCHED_FROM_NOTIFICATION_INTENT.equals(action)) {
                Bundle bundle = intent.getExtras();
                try {

                    HashMap<String, Object> teakReward = (HashMap<String, Object>) bundle.getSerializable("teakReward");
                    if (teakReward != null) {
                        Log.d(LOG_TAG, teakReward.toString());
                    }

                    if (bundle.getString("teakDeepLinkPath") != null) {
                        Log.d(LOG_TAG, bundle.getString("teakDeepLinkPath"));
                        HashMap<String, Object> teakDeepLinkQueryParameters = (HashMap<String, Object>) bundle.getSerializable("teakDeepLinkQueryParameters");
                        if (teakDeepLinkQueryParameters != null) {
                            Log.d(LOG_TAG, teakDeepLinkQueryParameters.toString());
                        }
                    }
                } catch(Exception e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Teak.onCreate(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TeakNotification.LAUNCHED_FROM_NOTIFICATION_INTENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter);

        Teak.identifyUser("demo-app-thingy-3");
        //Teak.trackEvent("actionid","objecttypeid", "instanceid");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Teak.onNewIntent(intent);
        super.onNewIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Teak.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void attachBaseContext(Context base) {
        Log.d(LOG_TAG, "Attach base context!");
        super.attachBaseContext(base);
    }

    public void testNotification(View view) {
        TeakNotification.scheduleNotification("test", "Some text!", 0);
    }

    public void crashApp(View view) {
        throw new RuntimeException("I crashed the app!");
    }
}
