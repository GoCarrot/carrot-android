package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import io.teak.sdk.Teak;

public class GetConfigurationFunction implements FREFunction {
    public enum ConfigurationType {
        AppConfiguration,
        DeviceConfiguration
    }
    private final ConfigurationType configurationType;

    public GetConfigurationFunction(final ConfigurationType configurationType) {
        this.configurationType = configurationType;
    }

    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            switch (this.configurationType) {
                case AppConfiguration: {
                    return FREObject.newObject(Teak.getAppConfiguration());
                }
                case DeviceConfiguration: {
                    return FREObject.newObject(Teak.getDeviceConfiguration());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
