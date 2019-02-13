package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

import io.teak.sdk.Teak;

public class TrackEventFunction implements FREFunction {
    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            Teak.trackEvent(argv[0].getAsString(), argv[1].getAsString(), argv[2].getAsString());
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        return null;
    }
}
