package io.teak.app.test;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import io.teak.sdk.Teak;
import io.teak.sdk.IObjectFactory;

public class MainActivity extends AppCompatActivity {
    public static IObjectFactory whateverFactory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Teak.onCreate(this, whateverFactory);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Teak.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }
}
