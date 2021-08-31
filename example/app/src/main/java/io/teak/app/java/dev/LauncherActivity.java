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
        final Intent launchIntent = getIntent();
        if (launchIntent != null) {
            launchMainActivity.setData(launchIntent.getData());
            launchMainActivity.putExtras(launchIntent);
        }
        this.startActivity(launchMainActivity);
        this.finish();
    }
}
