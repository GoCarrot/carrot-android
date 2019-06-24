package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import io.teak.sdk.Teak;

public class OpenSettingsAppToThisAppsSettingsFunction implements FREFunction {
    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            return FREObject.newObject(Teak.openSettingsAppToThisAppsSettings());
        } catch (Exception ignored) {
        }
        return null;
    }
}
