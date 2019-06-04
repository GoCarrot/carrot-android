package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import io.teak.sdk.Teak;

public class TrackEventFunction implements FREFunction {
    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            if (argv.length > 3) {
                Teak.incrementEvent(Extension.nullOrString(argv[0]),
                    Extension.nullOrString(argv[1]),
                    Extension.nullOrString(argv[2]),
                    Extension.zeroOrLong(argv[3]));
            } else {
                Teak.trackEvent(Extension.nullOrString(argv[0]),
                    Extension.nullOrString(argv[1]),
                    Extension.nullOrString(argv[2]));
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        return null;
    }
}
