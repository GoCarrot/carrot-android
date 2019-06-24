package io.teak.sdk.wrapper.unity;

import android.content.Intent;
import android.os.Bundle;
import com.unity3d.player.UnityPlayerActivity;
import io.teak.sdk.Teak;
import io.teak.sdk.Unobfuscable;

public class TeakUnityPlayerActivity extends UnityPlayerActivity implements Unobfuscable {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Teak.onCreate(this);
        super.onCreate(savedInstanceState);
        TeakUnity.initialize();
    }
}
