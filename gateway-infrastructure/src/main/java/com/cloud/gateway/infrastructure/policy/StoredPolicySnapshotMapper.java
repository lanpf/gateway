package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialId;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.CredentialForwardingPolicy;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.RequestSignatureChannelCredential;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class StoredPolicySnapshotMapper {
    public StoredPolicySnapshot toStore(IngressPolicySnapshot source) {
        IngressPolicyContent content = source.content();
        return new StoredPolicySnapshot(
                source.snapshotVersion(),
                toStoredEndpoints(content.endpointCatalog()),
                toStoredClientApps(content.clientAppPolicies()),
                toStoredChannelAccess(content.channelAccessPolicies()),
                toStoredChannelCredentials(content.channelCredentialPolicies()),
                toStoredRouteAccess(content.routeAccessPolicies()),
                source.contentHash(),
                source.publishedAt());
    }

    public IngressPolicySnapshot toDomain(StoredPolicySnapshot source) {
        return new IngressPolicySnapshot(source.snapshotVersion(), toDomainContent(source),
                source.contentHash(), source.publishedAt());
    }

    public IngressPolicyContent toDomainContent(StoredPolicySnapshot source) {
        return new IngressPolicyContent(toDomainEndpointCatalog(source.endpoints()),
                toDomainClientApps(source.clientApps()), toDomainChannelAccess(source.channelAccess()),
                toDomainChannelCredentials(source.channelCredentials()), toDomainRouteAccess(source.routes()));
    }

    public List<StoredPolicySnapshot.Endpoint> toStoredEndpoints(EndpointCatalog source) {
        return source.definitions().stream()
                .sorted(Comparator.comparing(value -> value.endpointId().value()))
                .map(this::endpointDocument)
                .toList();
    }

    public Map<String, StoredPolicySnapshot.ClientApp> toStoredClientApps(
            Map<ClientAppId, IngressClientAppPolicy> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> new StoredPolicySnapshot.ClientApp(
                        entry.getValue().status(),
                        entry.getValue().platform(),
                        entry.getValue().allowedBrowserOrigins()),
                (left, right) -> left,
                TreeMap::new));
    }

    public Map<String, StoredPolicySnapshot.ChannelAccess> toStoredChannelAccess(
            Map<ChannelCode, IngressChannelAccessPolicy> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> new StoredPolicySnapshot.ChannelAccess(entry.getValue().allowedClientAppIds().stream()
                        .map(ClientAppId::value).collect(Collectors.toUnmodifiableSet())),
                (left, right) -> left,
                TreeMap::new));
    }

    public Map<String, StoredPolicySnapshot.ChannelCredential> toStoredChannelCredentials(
            Map<ChannelCode, IngressChannelCredentialPolicy> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> new StoredPolicySnapshot.ChannelCredential(
                        credentialDocument(entry.getValue().activeCredential()),
                        entry.getValue().graceCredentialOptional().map(this::credentialDocument).orElse(null)),
                (left, right) -> left,
                TreeMap::new));
    }

    public Map<String, StoredPolicySnapshot.RouteAccess> toStoredRouteAccess(
            Map<EndpointId, IngressRouteAccessPolicy> source
    ) {
        return source.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().value(),
                entry -> routeDocument(entry.getValue()),
                (left, right) -> left,
                TreeMap::new));
    }

    public EndpointCatalog toDomainEndpointCatalog(List<StoredPolicySnapshot.Endpoint> source) {
        return new EndpointCatalog(source.stream().map(this::endpointDomain).toList());
    }

    public Map<ClientAppId, IngressClientAppPolicy> toDomainClientApps(
            Map<String, StoredPolicySnapshot.ClientApp> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new ClientAppId(entry.getKey()),
                        entry -> new IngressClientAppPolicy(new ClientAppId(entry.getKey()),
                                entry.getValue().status(),
                                entry.getValue().platform(),
                                entry.getValue().allowedBrowserOrigins())));
    }

    public Map<ChannelCode, IngressChannelAccessPolicy> toDomainChannelAccess(
            Map<String, StoredPolicySnapshot.ChannelAccess> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new ChannelCode(entry.getKey()),
                        entry -> new IngressChannelAccessPolicy(new ChannelCode(entry.getKey()),
                                entry.getValue().allowedClientAppIds().stream()
                                        .map(ClientAppId::new).collect(Collectors.toUnmodifiableSet()))));
    }

    public Map<ChannelCode, IngressChannelCredentialPolicy> toDomainChannelCredentials(
            Map<String, StoredPolicySnapshot.ChannelCredential> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new ChannelCode(entry.getKey()),
                        entry -> new IngressChannelCredentialPolicy(new ChannelCode(entry.getKey()),
                                credentialDomain(entry.getKey(), entry.getValue().activeCredential()),
                                entry.getValue().graceCredential() == null ? null
                                        : credentialDomain(entry.getKey(), entry.getValue().graceCredential()))));
    }

    public Map<EndpointId, IngressRouteAccessPolicy> toDomainRouteAccess(
            Map<String, StoredPolicySnapshot.RouteAccess> source
    ) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> new EndpointId(entry.getKey()),
                        entry -> routeDomain(entry.getKey(), entry.getValue())));
    }

    private StoredPolicySnapshot.Endpoint endpointDocument(EndpointDefinition source) {
        return new StoredPolicySnapshot.Endpoint(source.endpointId().value(), source.ownerService(),
                source.matchers().stream().map(matcher -> new StoredPolicySnapshot.Matcher(
                        matcher.httpMethod(), matcher.pathPattern())).toList(), source.deliveryRouteId());
    }

    private EndpointDefinition endpointDomain(StoredPolicySnapshot.Endpoint source) {
        return new EndpointDefinition(new EndpointId(source.endpointId()), source.ownerService(),
                source.matchers().stream().map(matcher -> new EndpointMatcher(
                        matcher.httpMethod(), matcher.pathPattern())).toList(), source.deliveryRouteId());
    }

    private StoredPolicySnapshot.Credential credentialDocument(RequestSignatureChannelCredential source) {
        return new StoredPolicySnapshot.Credential(source.credentialId().value(), source.validFrom(),
                source.expiresAt(), source.signaturePayloadContract(), source.secretRef());
    }

    private RequestSignatureChannelCredential credentialDomain(
            String channelCode, StoredPolicySnapshot.Credential source) {
        return new RequestSignatureChannelCredential(new ChannelCredentialId(source.credentialId()),
                source.validFrom(), source.expiresAt(), new ChannelCode(channelCode),
                source.signaturePayloadContract(), source.secretRef());
    }

    private StoredPolicySnapshot.RouteAccess routeDocument(IngressRouteAccessPolicy source) {
        CredentialForwardingPolicy forwarding = source.credentialForwardingPolicy();
        return new StoredPolicySnapshot.RouteAccess(source.channelCredentialRequirement(),
                source.channelRequirement(), source.subjectRequirement(), source.requiredScopes(),
                source.requiredAudiences(), new StoredPolicySnapshot.Forwarding(
                        forwarding.authorizationHeader(), forwarding.cookies(), forwarding.allowedCookieNames()),
                source.enabled());
    }

    private IngressRouteAccessPolicy routeDomain(String endpointId, StoredPolicySnapshot.RouteAccess source) {
        StoredPolicySnapshot.Forwarding forwarding = source.forwarding();
        return new IngressRouteAccessPolicy(new EndpointId(endpointId), source.channelCredentialRequirement(),
                source.channelRequirement(), source.subjectRequirement(), source.requiredScopes(),
                source.requiredAudiences(), new CredentialForwardingPolicy(forwarding.authorizationHeader(),
                        forwarding.cookies(), forwarding.allowedCookieNames()), source.enabled());
    }
}
