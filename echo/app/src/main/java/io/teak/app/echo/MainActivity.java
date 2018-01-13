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
    private void processIntent(@NonNull Intent intent) {
        TextView textView = findViewById(R.id.mainTextView);
        textView.setText("");

        int result = Activity.RESULT_CANCELED;
        if (intent.hasCategory("io.teak.sdk.test")) {
            try {
                final String teaklaunchUrl = intent.getStringExtra("teak-launch-url");
                final Uri teakLaunchUri = Uri.parse(teaklaunchUrl);
                textView.setText(teaklaunchUrl);
                result = Activity.RESULT_OK;

                // Delay, for visual debugging
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable(){
                    @Override
                    public void run(){
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, teakLaunchUri);
                        startActivity(browserIntent);
                    }
                }, 1000);
            } catch (Exception e) {
                textView.setText(Log.getStackTraceString(e));
                Log.e("Teak-Echo", Log.getStackTraceString(e));
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
