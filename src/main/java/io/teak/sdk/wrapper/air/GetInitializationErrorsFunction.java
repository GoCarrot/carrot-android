package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class GetInitializationErrorsFunction implements FREFunction {
    private final String initializationErrors;

    GetInitializationErrorsFunction(String errorsOrNull) {
        super();
        this.initializationErrors = errorsOrNull;
    }

    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            return FREObject.newObject(this.initializationErrors);
        } catch (Exception ignored) {
            return null;
        }
    }
}
