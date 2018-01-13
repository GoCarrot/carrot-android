package io.teak.app.echo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        int result = Activity.RESULT_CANCELED;
        if (intent != null && intent.hasCategory("io.teak.sdk.test")) {
            try {
                final String teaklaunchUrl = intent.getStringExtra("teak-launch-url");
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(teaklaunchUrl));
                startActivity(browserIntent);
                result = Activity.RESULT_OK;
            } catch (Exception e) {
                Log.e("Teak-Echo", Log.getStackTraceString(e));
            }
        }
        setResult(result);
    }
}
