package com.cloud.gateway.interfaces.rest;

import com.cloud.gateway.api.facade.admin.definition.SaveAppApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveClientApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveClientCredentialApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveEndpointApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetAppStatusApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetClientStatusApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SetEndpointStatusApiCommand;
import com.cloud.gateway.api.facade.admin.snapshot.ReleaseSnapshotApiCommand;
import com.cloud.gateway.api.facade.admin.snapshot.RollbackSnapshotApiCommand;
import java.util.Set;

/** HTTP request DTO to API command translation; no business logic. */
public interface GatewayPolicyAdminRestMapper {

    default SaveAppApiCommand toCommand(GatewayPolicyAdminRequest.SaveAppAccess source) {
        return new SaveAppApiCommand(source.getExpectedSourceRevision(), source.getTargetAppId(),
                source.getPlatform(), Set.copyOf(source.getAllowedOrigins()));
    }

    default SaveClientApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientAccess source) {
        return new SaveClientApiCommand(source.getExpectedSourceRevision(), source.getClientId(),
                Set.copyOf(source.getAllowedAppIds()));
    }

    default SaveClientCredentialApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientCredential source) {
        return new SaveClientCredentialApiCommand(source.getExpectedSourceRevision(), source.getClientId(),
                credential(source.getActiveCredential()),
                source.getGraceCredential() == null ? null : credential(source.getGraceCredential()));
    }

    default SaveEndpointApiCommand toCommand(GatewayPolicyAdminRequest.SaveEndpointAccess source) {
        return new SaveEndpointApiCommand(source.getExpectedSourceRevision(), source.getEndpointId(),
                source.getServiceId(), source.getRouteId(),
                source.getMatchers().stream()
                        .map(matcher -> new SaveEndpointApiCommand.Matcher(matcher.getHttpMethod(),
                                matcher.getPathPattern()))
                        .toList(),
                Set.copyOf(source.getTags()), source.getClientVerificationRequirement(),
                source.getSubjectAuthenticationRequirement(), Set.copyOf(source.getRequiredScopes()),
                Set.copyOf(source.getRequiredAudiences()));
    }

    default SetAppStatusApiCommand toCommand(GatewayPolicyAdminRequest.SetAppStatus source) {
        return new SetAppStatusApiCommand(source.getExpectedSourceRevision(), source.getAppId(), source.getStatus());
    }

    default SetClientStatusApiCommand toCommand(GatewayPolicyAdminRequest.SetClientStatus source) {
        return new SetClientStatusApiCommand(source.getExpectedSourceRevision(), source.getClientId(),
                source.getStatus());
    }

    default SetEndpointStatusApiCommand toCommand(GatewayPolicyAdminRequest.SetEndpointStatus source) {
        return new SetEndpointStatusApiCommand(source.getExpectedSourceRevision(), source.getEndpointId(),
                source.getStatus());
    }

    default RollbackSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.RollbackAccessSnapshot source) {
        return new RollbackSnapshotApiCommand(source.getTargetSnapshotVersion());
    }

    default ReleaseSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.PublishAccessSnapshot source) {
        return new ReleaseSnapshotApiCommand();
    }

    private static SaveClientCredentialApiCommand.Credential credential(
            GatewayPolicyAdminRequest.ClientCredential source) {
        return new SaveClientCredentialApiCommand.Credential(source.getCredentialId(), source.getSecretRef(),
                source.getSignatureContract(), source.getValidFrom(), source.getExpiresAt());
    }
}
