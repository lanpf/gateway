package com.cloud.gateway.interfaces.facade.mapper;

import com.cloud.gateway.api.policy.ChannelCredentialApiCommand;
import com.cloud.gateway.api.policy.CredentialForwardingApiCommand;
import com.cloud.gateway.api.policy.PolicyPublicationApiCommandOutput;
import com.cloud.gateway.api.policy.RollbackPolicySnapshotApiCommand;
import com.cloud.gateway.api.policy.PublishPolicySnapshotApiCommand;
import com.cloud.gateway.api.policy.SaveChannelAccessPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveChannelCredentialPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveClientAppPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveRouteAccessPolicyApiCommand;
import com.cloud.gateway.application.policy.PolicyPublicationOutput;
import com.cloud.gateway.application.policy.RollbackPolicySnapshotCommand;
import com.cloud.gateway.application.policy.PublishPolicySnapshotCommand;
import com.cloud.gateway.application.policy.SaveChannelAccessPolicyCommand;
import com.cloud.gateway.application.policy.SaveChannelCredentialPolicyCommand;
import com.cloud.gateway.application.policy.SaveClientAppPolicyCommand;
import com.cloud.gateway.application.policy.SaveRouteAccessPolicyCommand;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.AuthorizationHeaderPolicy;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialId;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.ClientAppStatus;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.CredentialForwardingPolicy;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.RequestSignatureChannelCredential;
import com.cloud.gateway.domain.policy.SignaturePayloadContract;
import com.cloud.gateway.domain.policy.SubjectRequirement;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface GatewayPolicyApiMapper {
    default SaveClientAppPolicyCommand toCommand(SaveClientAppPolicyApiCommand source) {
        return new SaveClientAppPolicyCommand(source.expectedSourceRevision(),
                new IngressClientAppPolicy(new ClientAppId(source.clientAppId()),
                        ClientAppStatus.valueOf(source.status().name()), source.platform()));
    }

    default SaveChannelAccessPolicyCommand toCommand(SaveChannelAccessPolicyApiCommand source) {
        return new SaveChannelAccessPolicyCommand(source.expectedSourceRevision(),
                new IngressChannelAccessPolicy(new ChannelCode(source.channelCode()), source.allowedClientAppIds().stream()
                        .map(ClientAppId::new).collect(java.util.stream.Collectors.toUnmodifiableSet())));
    }

    default SaveChannelCredentialPolicyCommand toCommand(SaveChannelCredentialPolicyApiCommand source) {
        return new SaveChannelCredentialPolicyCommand(source.expectedSourceRevision(),
                new IngressChannelCredentialPolicy(new ChannelCode(source.channelCode()),
                        credential(source.channelCode(), source.activeCredential()),
                        credential(source.channelCode(), source.graceCredential())));
    }

    default SaveRouteAccessPolicyCommand toCommand(SaveRouteAccessPolicyApiCommand source) {
        CredentialForwardingApiCommand forwarding = source.credentialForwardingPolicy();
        return new SaveRouteAccessPolicyCommand(source.expectedSourceRevision(),
                new IngressRouteAccessPolicy(new EndpointId(source.endpointId()),
                        ChannelCredentialRequirement.valueOf(source.channelCredentialRequirement().name()),
                        ChannelRequirement.valueOf(source.channelRequirement().name()),
                        SubjectRequirement.valueOf(source.subjectRequirement().name()),
                        source.requiredScopes(), source.requiredAudiences(),
                        new CredentialForwardingPolicy(
                                AuthorizationHeaderPolicy.valueOf(forwarding.authorizationHeader().name()),
                                CookieForwardingMode.valueOf(forwarding.cookies().name()),
                                forwarding.allowedCookieNames()), source.enabled()));
    }

    default PolicyPublicationApiCommandOutput toApiOutput(PolicyPublicationOutput source) {
        return new PolicyPublicationApiCommandOutput(source.snapshotVersion(), source.contentHash());
    }

    default RollbackPolicySnapshotCommand toCommand(RollbackPolicySnapshotApiCommand source) {
        return new RollbackPolicySnapshotCommand(source.targetSnapshotVersion());
    }

    default PublishPolicySnapshotCommand toCommand(PublishPolicySnapshotApiCommand source) {
        return new PublishPolicySnapshotCommand();
    }

    private static RequestSignatureChannelCredential credential(String channelCode, ChannelCredentialApiCommand source) {
        if (source == null) return null;
        return new RequestSignatureChannelCredential(new ChannelCredentialId(source.credentialId()),
                source.validFrom(), source.expiresAt(), new ChannelCode(channelCode),
                SignaturePayloadContract.valueOf(source.signaturePayloadContract().name()), source.secretRef());
    }
}
