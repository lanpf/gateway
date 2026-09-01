package com.cloud.gateway.interfaces.webflux;

final class GatewayRequestHeaders {
    public static final String SUBJECT_TYPE = "X-Subject-Type";
    public static final String SIGNATURE = "X-Signature";
    public static final String TIMESTAMP = "X-Timestamp";
    public static final String BROWSER_SESSION_COOKIE = "BROWSER_SESSION";
    private GatewayRequestHeaders() { }
}
