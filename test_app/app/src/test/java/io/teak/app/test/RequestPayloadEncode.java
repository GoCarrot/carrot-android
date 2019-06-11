package io.teak.app.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import io.teak.sdk.Request;
import io.teak.sdk.core.Session;

@RunWith(MockitoJUnitRunner.class)
public class RequestPayloadEncode {
    @Test
    public void EmptyPayload() throws UnsupportedEncodingException {
        final HashMap<String, Object> emptyPayload = new HashMap<>();
        Assert.assertEquals("", Request.Payload.payloadToString(emptyPayload, false));
    }

    @Test
    public void PayloadContainingOnlyNullValue() throws UnsupportedEncodingException {
        final HashMap<String, Object> payloadContainingNull = new HashMap<>();
        payloadContainingNull.put("value_is_null", null);
        Assert.assertEquals("", Request.Payload.payloadToString(payloadContainingNull, false));
    }

    @Test
    public void StringPayload() throws UnsupportedEncodingException {
        final HashMap<String, Object> payloadWithStringValue = new HashMap<>();
        payloadWithStringValue.put("value_is_string", "a_string");
        Assert.assertEquals("value_is_string=a_string", Request.Payload.payloadToString(payloadWithStringValue, false));

        payloadWithStringValue.put("value_is_string", "a string");
        Assert.assertEquals("value_is_string=a string", Request.Payload.payloadToString(payloadWithStringValue, false));
    }
}
