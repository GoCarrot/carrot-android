package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import io.teak.sdk.Teak;
import io.teak.sdk.raven.Raven;

public class ExceptionReportingTestFunction implements FREFunction {
    @Override
    @SuppressWarnings("deprecation")
    public FREObject call(FREContext context, FREObject[] argv) {
        Teak.log.exception(new Raven.ReportTestException(Teak.SDKVersion));
        return null;
    }
}
