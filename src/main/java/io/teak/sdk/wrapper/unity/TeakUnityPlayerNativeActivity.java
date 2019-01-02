package io.teak.sdk.wrapper.unity;

import android.util.Log;

import android.os.Bundle;

import io.teak.sdk.Unobfuscable;

public class TeakUnityPlayerNativeActivity extends TeakUnityPlayerActivity implements Unobfuscable {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e("Teak", "TeakUnityPlayerNativeActivity has been deprecated, please update your AndroidManifest to use TeakUnityPlayerActivity instead");
        super.onCreate(savedInstanceState);
    }
}
