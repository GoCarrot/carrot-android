package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREExtension;
import com.adobe.fre.FREInvalidObjectException;
import com.adobe.fre.FREObject;
import com.adobe.fre.FRETypeMismatchException;
import com.adobe.fre.FREWrongThreadException;

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

    static String nullOrString(FREObject obj) throws FREInvalidObjectException, FRETypeMismatchException, FREWrongThreadException {
        return obj == null ? null : obj.getAsString();
    }

    static long zeroOrLong(FREObject obj) throws FREInvalidObjectException, FRETypeMismatchException, FREWrongThreadException {
        return obj == null ? 0 : (long) obj.getAsDouble();
    }
}
