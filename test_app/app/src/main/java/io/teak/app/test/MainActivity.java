package io.teak.app.test;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import io.teak.sdk.Teak2;
import io.teak.sdk.ObjectFactory;

public class MainActivity extends AppCompatActivity {
    public static ObjectFactory whateverFactory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Teak2.onCreate(this, whateverFactory);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }
}
