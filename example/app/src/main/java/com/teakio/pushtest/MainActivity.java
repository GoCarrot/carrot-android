package com.teakio.pushtest;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import io.teak.sdk.Teak;
import io.teak.sdk.TeakNotification;

import android.view.View;

public class MainActivity extends AppCompatActivity {
    public static final String LOG_TAG = "Teak:Example";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Teak.onCreate(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
