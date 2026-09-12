package com.cloud.gateway.interfaces.facade.mapper.mapstruct;

import com.cloud.gateway.api.policy.CredentialForwardingApiCommand;
import com.cloud.gateway.api.policy.ReleaseSnapshotApiCommand;
import com.cloud.gateway.api.policy.SnapshotReleaseApiCommandOutput;
import com.cloud.gateway.api.policy.RollbackSnapshotApiCommand;
import com.cloud.gateway.api.policy.SaveClientApiCommand;
import com.cloud.gateway.api.policy.SaveAppApiCommand;
import com.cloud.gateway.api.policy.SaveEndpointApiCommand;
import com.cloud.gateway.application.policy.PolicyPublicationOutput;
import com.cloud.gateway.application.policy.RollbackPolicySnapshotCommand;
import com.cloud.gateway.application.policy.PublishPolicySnapshotCommand;
import com.cloud.gateway.application.policy.SaveClientAccessPolicyCommand;
import com.cloud.gateway.application.policy.SaveClientCredentialPolicyCommand;
import com.cloud.gateway.application.policy.SaveAppPolicyCommand;
import com.cloud.gateway.application.policy.SaveEndpointAccessPolicyCommand;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.interfaces.facade.mapper.GatewayPolicyApiMapper;
import com.cloud.gateway.interfaces.rest.mapstruct.PolicyAdminMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Generated carrier mappings delegate domain construction to explicit semantic methods. */
@Mapper(config = PolicyAdminMapperConfig.class)
public abstract class GatewayPolicyApiMapStructMapper implements GatewayPolicyApiMapper {
    @Override
    @Mapping(target = "policy", source = "source", qualifiedByName = "appPolicy")
    public abstract SaveAppPolicyCommand toCommand(SaveAppApiCommand source);

    @Override
    @Mapping(target = "policy", source = "source", qualifiedByName = "clientPolicy")
    public abstract SaveClientAccessPolicyCommand toCommand(SaveClientApiCommand source);

    @Override
    @Mapping(target = "clientId", source = "clientId", qualifiedByName = "clientId")
    @Mapping(target = "credentials", source = "source", qualifiedByName = "credentials")
    public abstract SaveClientCredentialPolicyCommand toCommand(SaveClientCredentialPolicyApiCommand source);

    @Override
    @Mapping(target = "policy", source = "source", qualifiedByName = "endpointPolicy")
    public abstract SaveEndpointAccessPolicyCommand toCommand(SaveEndpointApiCommand source);

    @Override
    public abstract SnapshotReleaseApiCommandOutput toApiOutput(PolicyPublicationOutput source);

    @Override
    public abstract RollbackPolicySnapshotCommand toCommand(RollbackSnapshotApiCommand source);

    @Override
    public abstract PublishPolicySnapshotCommand toCommand(ReleaseSnapshotApiCommand source);

    @Named("appPolicy")
    protected AppPolicy appPolicy(SaveAppApiCommand source) {
        return new AppPolicy(new AppId(source.appId()), source.platform(), source.allowedOrigins(),
                PolicyStatus.valueOf(source.status().name()));
    }

    @Named("clientPolicy")
    protected ClientPolicy clientPolicy(SaveClientApiCommand source) {
        return new ClientPolicy(clientId(source.clientId()), source.allowedAppIds().stream()
                .map(AppId::new).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                null, ClientStatus.valueOf(source.status().name()));
    }

    @Named("clientId")
    protected ClientId clientId(String value) {
        return new ClientId(value);
    }

    @Named("credentials")
    protected Credentials credentials(SaveClientCredentialPolicyApiCommand source) {
        return new Credentials(credential(source.clientId(), source.activeCredential()),
                credential(source.clientId(), source.graceCredential()));
    }

    @Named("endpointPolicy")
    protected EndpointPolicy endpointPolicy(SaveEndpointApiCommand source) {
        CredentialForwardingApiCommand forwarding = source.credentialForwarding();
        return new EndpointPolicy(new EndpointId(source.endpointId()),
                ClientVerificationRequirement.valueOf(source.clientRequirement().name()),
                ClientVerificationMode.valueOf(source.clientCredentialRequirement().name()),
                SubjectAuthencationRequirement.valueOf(source.subjectRequirement().name()),
                source.requiredScopes(), source.requiredAudiences(),
                new CredentialForwarding(AuthorizationHeaderForwardingMode.valueOf(forwarding.authorizationHeader().name()),
                        CookieForwardingMode.valueOf(forwarding.cookies().name()), forwarding.allowedCookieNames()),
                EndpointStatus.valueOf(source.status().name()));
    }

    private static Credential credential(String clientId, ClientCredentialApiCommand source) {
        if (source == null) {
            return null;
        }
        return new Credential(new CredentialId(source.credentialId()), new ClientId(clientId),
                SignatureContract.valueOf(source.signaturePayloadContract().name()), source.secretRef(),
                source.validFrom(), source.expiresAt());
    }
}
