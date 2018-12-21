package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREExtension;

import io.teak.sdk.Unobfuscable;

public class Extension implements FREExtension, Unobfuscable {
    public static FREContext context;

    @Override
    public FREContext createContext(String extId) {
        context = new ExtensionContext();
        return context;
    }

    @Override
    public void dispose() {
        context = null;
    }

    @Override
    public void initialize() {
        // None
    }
}
