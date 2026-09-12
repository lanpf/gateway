package com.cloud.gateway.api.constants;

public final class GatewayPathApiConstants {
    public static final String SERVICE_NAME = "gateway";
    public static final String ADMIN_BASE_PATH = "/admin/gateway";

    public static final String ADMIN_POLICIES = ADMIN_BASE_PATH + "/policies";
    public static final String ADMIN_POLICIES_APP = ADMIN_POLICIES + "/app";
    public static final String ADMIN_POLICIES_CLIENT_ACCESS = ADMIN_POLICIES + "/client-access";
    public static final String ADMIN_POLICIES_CLIENT_CREDENTIAL = ADMIN_POLICIES + "/client-credential";
    public static final String ADMIN_POLICIES_ENDPOINT_ACCESS = ADMIN_POLICIES + "/endpoint-access";

    public static final String ADMIN_POLICY_CONFIGURATION = ADMIN_POLICIES + "/configuration";

    public static final String ADMIN_SNAPSHOT = ADMIN_BASE_PATH + "/snapshot";
    public static final String ADMIN_SNAPSHOT_PUBLISH = ADMIN_SNAPSHOT + "/publish";
    public static final String ADMIN_SNAPSHOT_ROLLBACK = ADMIN_SNAPSHOT + "/rollback";

    private GatewayPathApiConstants() { }
}
