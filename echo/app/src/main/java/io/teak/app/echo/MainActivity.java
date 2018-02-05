package io.teak.app.echo;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "Teak-Echo";

    private void processIntent(@NonNull Intent intent) {
        TextView textView = findViewById(R.id.mainTextView);
        textView.setText("");

        Log.d(LOG_TAG, "Received intent: " + intent.toString());
        int result = Activity.RESULT_CANCELED;
        if (intent.hasCategory("io.teak.sdk.test") && !intent.getBooleanExtra("teak-echo-handled", false)) {
            intent.putExtra("teak-echo-handled", true);
            try {
                final String teaklaunchUrl = intent.getStringExtra("teak-launch-url");
                final String teakPackageName = intent.getStringExtra("teak-package-name");
                final long teakEchoDelayMs = intent.getLongExtra("teak-echo-delay-ms", 1000L);

                Log.d(LOG_TAG, "teak-launch-url = " + teaklaunchUrl);
                Log.d(LOG_TAG, "teak-package-name = " + teakPackageName);
                Log.d(LOG_TAG, "teak-echo-delay-ms = " + teakEchoDelayMs);

                final Intent uriIntent = new Intent(Intent.ACTION_VIEW);
                uriIntent.setData(Uri.parse(teaklaunchUrl));
                uriIntent.setPackage(teakPackageName);

                textView.setText(teaklaunchUrl);

                if (uriIntent.resolveActivity(this.getPackageManager()) != null) {
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(LOG_TAG, "Starting activity: " + uriIntent.toString());
                            startActivity(uriIntent);
                        }
                    }, teakEchoDelayMs);
                    result = Activity.RESULT_OK;
                } else {
                    Log.e(LOG_TAG, "resolveActivity() failed for: " + uriIntent.toString());
                }
            } catch (Exception e) {
                textView.setText(Log.getStackTraceString(e));
                Log.e(LOG_TAG, Log.getStackTraceString(e));
            }
        }
        setResult(result);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) {
                Intent intent = activity.getIntent();
                if (intent == null) {
                    intent = new Intent();
                }
                processIntent(intent);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                Intent intent = activity.getIntent();
                if (intent == null) {
                    intent = new Intent();
                }
                processIntent(intent);
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }
}
