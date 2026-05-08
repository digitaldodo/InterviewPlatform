package com.interview.platform.api;

import org.slf4j.MDC;

public final class RequestContext {
    public static final String REQUEST_ID_KEY = "requestId";

    private RequestContext() {
    }

    public static String requestId() {
        String requestId = MDC.get(REQUEST_ID_KEY);
        return requestId == null || requestId.isBlank() ? "unknown" : requestId;
    }
}
