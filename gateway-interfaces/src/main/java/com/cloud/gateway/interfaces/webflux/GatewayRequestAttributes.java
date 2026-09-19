package com.cloud.gateway.interfaces.webflux;

/**
 * Shared adapter keys; values are immutable per-request facts, not a refreshable context. The
 * prepare stage's output crosses SCG routing as one typed value rather than as loose field keys, so
 * the handoff to the later stages cannot drift from what that stage actually produced.
 */
public final class GatewayRequestAttributes {
    public static final String PREPARED = "gateway.access.prepared";
    /** Denormalized view of the unique matched endpoint; absent when the match was not unique. */
    public static final String ENDPOINT = "gateway.access.endpoint";
    public static final String LOCAL_ROUTE_ID = "gateway-admin";
    public static final String TRUSTED_CONTEXT = "gateway.access.trustedContext";
    public static final String REQUEST_BODY_PRESENT = "gateway.request.bodyPresent";
    public static final String PREPARATION_COMPLETE = "gateway.access.preparationComplete";
    private GatewayRequestAttributes() { }
}
