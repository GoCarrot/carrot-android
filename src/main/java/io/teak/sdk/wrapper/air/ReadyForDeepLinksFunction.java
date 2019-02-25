package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class ReadyForDeepLinksFunction implements FREFunction {
    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        try {
            if (context instanceof  ExtensionContext) {
                final ExtensionContext extensionContext = (ExtensionContext) context;
                if (extensionContext.teakInterface != null) {
                    extensionContext.teakInterface.readyForDeepLinks();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
