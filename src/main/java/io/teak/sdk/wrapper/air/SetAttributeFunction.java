package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

import io.teak.sdk.Teak;

public class SetAttributeFunction implements FREFunction {
    public enum FunctionType {
        Numeric,
        String
    }

    private final FunctionType functionType;

    SetAttributeFunction(FunctionType functionType) {
        this.functionType = functionType;
    }

    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            switch (this.functionType) {
                case Numeric: {
                    Teak.setNumericAttribute(argv[0].getAsString(), argv[1].getAsDouble());
                } break;

                case String: {
                    Teak.setStringAttribute(argv[0].getAsString(), argv[1].getAsString());
                } break;
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        return null;
    }
}
