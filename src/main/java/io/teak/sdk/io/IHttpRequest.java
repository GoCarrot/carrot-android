package io.teak.sdk.io;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

public interface IHttpRequest {
    class Response {
        public final int statusCode;
        public final String body;
        public final Map<String, List<String>> headers;

        Response(int statusCode, String body, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }

        public static final Response ERROR_RESPONSE = new Response(599, "{}", null);
    }

    @Nullable
    Response synchronousRequest(URL url, Map<String, Object> payload, String sig) throws IOException;
}
