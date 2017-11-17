package io.teak.app.notification_visuals;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import io.teak.sdk.Teak;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Teak.onCreate(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
