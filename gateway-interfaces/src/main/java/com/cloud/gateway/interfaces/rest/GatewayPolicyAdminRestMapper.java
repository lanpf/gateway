package com.cloud.gateway.interfaces.rest;

import com.cloud.gateway.api.policy.ChannelCredentialApiCommand;
import com.cloud.gateway.api.policy.CredentialForwardingApiCommand;
import com.cloud.gateway.api.policy.SaveChannelAccessPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveChannelCredentialPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveClientAppPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveRouteAccessPolicyApiCommand;
import com.cloud.gateway.api.policy.RollbackPolicySnapshotApiCommand;
import com.cloud.gateway.api.policy.PublishPolicySnapshotApiCommand;

final class GatewayPolicyAdminRestMapper {
    private GatewayPolicyAdminRestMapper() { }

    static SaveClientAppPolicyApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientAppPolicy request) {
        return new SaveClientAppPolicyApiCommand(request.getExpectedSourceRevision(), request.getTargetClientAppId(),
                request.getStatus(), request.getPlatform());
    }

    static SaveChannelAccessPolicyApiCommand toCommand(GatewayPolicyAdminRequest.SaveChannelAccessPolicy request) {
        return new SaveChannelAccessPolicyApiCommand(request.getExpectedSourceRevision(), request.getChannelCode(),
                request.getAllowedClientAppIds());
    }

    static SaveChannelCredentialPolicyApiCommand toCommand(GatewayPolicyAdminRequest.SaveChannelCredentialPolicy request) {
        return new SaveChannelCredentialPolicyApiCommand(request.getExpectedSourceRevision(), request.getChannelCode(),
                credential(request.getActiveCredential()), credential(request.getGraceCredential()));
    }

    static SaveRouteAccessPolicyApiCommand toCommand(GatewayPolicyAdminRequest.SaveRouteAccessPolicy request) {
        GatewayPolicyAdminRequest.CredentialForwarding forwarding = request.getCredentialForwardingPolicy();
        return new SaveRouteAccessPolicyApiCommand(request.getExpectedSourceRevision(), request.getEndpointId(),
                request.getChannelCredentialRequirement(), request.getChannelRequirement(), request.getSubjectRequirement(),
                request.getRequiredScopes(), request.getRequiredAudiences(), new CredentialForwardingApiCommand(
                        forwarding.getAuthorizationHeader(), forwarding.getCookies(), forwarding.getAllowedCookieNames()),
                request.isEnabled());
    }

    static RollbackPolicySnapshotApiCommand toCommand(GatewayPolicyAdminRequest.RollbackPolicySnapshot request) {
        return new RollbackPolicySnapshotApiCommand(request.getTargetSnapshotVersion());
    }

    static PublishPolicySnapshotApiCommand toCommand(GatewayPolicyAdminRequest.PublishPolicySnapshot request) {
        return new PublishPolicySnapshotApiCommand();
    }

    private static ChannelCredentialApiCommand credential(GatewayPolicyAdminRequest.ChannelCredential request) {
        if (request == null) return null;
        return new ChannelCredentialApiCommand(request.getCredentialId(), request.getValidFrom(), request.getExpiresAt(),
                request.getSignaturePayloadContract(), request.getSecretRef());
    }
}
