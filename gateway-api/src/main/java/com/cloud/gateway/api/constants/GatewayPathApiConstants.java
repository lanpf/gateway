package com.cloud.gateway.api.constants;

public final class GatewayPathApiConstants {
    public static final String SERVICE_NAME = "gateway";
    public static final String ADMIN_BASE_PATH = "/admin";

    public static final String ADMIN_POLICIES = ADMIN_BASE_PATH + "/policies";
    public static final String ADMIN_POLICIES_CLIENT_APP = ADMIN_POLICIES + "/client-app";
    public static final String ADMIN_POLICIES_CHANNEL_ACCESS = ADMIN_POLICIES + "/channel-access";
    public static final String ADMIN_POLICIES_CHANNEL_CREDENTIAL = ADMIN_POLICIES + "/channel-credential";
    public static final String ADMIN_POLICIES_ROUTE_ACCESS = ADMIN_POLICIES + "/route-access";

    public static final String ADMIN_SNAPSHOT = ADMIN_BASE_PATH + "/snapshot";
    public static final String ADMIN_SNAPSHOT_PUBLISH = ADMIN_SNAPSHOT + "/publish";
    public static final String ADMIN_SNAPSHOT_ROLLBACK = ADMIN_SNAPSHOT + "/rollback";

    private GatewayPathApiConstants() { }
}
