package com.cloud.gateway.interfaces.webflux;

/** Shared adapter keys; values are immutable per-request facts, not a refreshable context. */
public final class GatewayRequestAttributes {
    public static final String SNAPSHOT = "gateway.policy.snapshot";
    public static final String ENDPOINT = "gateway.policy.endpoint";
    public static final String EVIDENCE = "gateway.policy.evidence";
    public static final String ENDPOINT_MATCH = "gateway.policy.endpointMatch";
    public static final String LOCAL_ROUTE_ID = "gateway-admin";
    public static final String TRUSTED_CONTEXT = "gateway.policy.trustedContext";
    public static final String REQUEST_BODY_PRESENT = "gateway.request.bodyPresent";
    public static final String APPLIED_IP_RULES = "gateway.policy.appliedIpRules";
    public static final String PREPARATION_COMPLETE = "gateway.policy.preparationComplete";
    private GatewayRequestAttributes() { }
}
