package io.teak.sdk.wrapper.air;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import io.teak.sdk.Teak;
import io.teak.sdk.json.JSONObject;

public class GetVersionFunction implements FREFunction {
    @Override
    public FREObject call(FREContext context, FREObject[] argv) {
        JSONObject obj = new JSONObject(Teak.Version);
        try {
            return FREObject.newObject(obj.toString());
        } catch (Exception ignored) {
        }
        return null;
    }
}
