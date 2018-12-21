package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

import java.util.Arrays;

import io.teak.sdk.Teak;
import io.teak.sdk.json.JSONArray;

public class IdentifyUserFunction implements FREFunction {
    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        String[] optOut = new String[] {};
        try {
            final JSONArray jsonList = new JSONArray(argv[1].getAsString());
            optOut = Arrays.copyOf(jsonList.toList().toArray(), jsonList.length(), String[].class);
        } catch (Exception e) {
            Teak.log.exception(e);
        }

        try {
            Teak.identifyUser(argv[0].getAsString(), optOut);
        } catch (Exception e) {
            Teak.log.exception(e);
        }
        return null;
    }
}
