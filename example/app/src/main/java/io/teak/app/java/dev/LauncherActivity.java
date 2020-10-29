package io.teak.app.java.dev;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class LauncherActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent launchMainActivity = new Intent(this, MainActivity.class);
        this.startActivity(launchMainActivity);
        this.finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final Intent launchMainActivity = new Intent(this, MainActivity.class);
        launchMainActivity.setData(intent.getData());
        this.startActivity(launchMainActivity);
        this.finish();
    }
}
