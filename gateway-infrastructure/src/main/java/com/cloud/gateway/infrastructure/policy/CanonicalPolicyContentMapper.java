package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.RequestSignatureChannelCredential;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CanonicalPolicyContentMapper {
    private static final String MATCHER_SEPARATOR = " ";

    public CanonicalPolicyContent toCanonical(IngressPolicyContent source) {
        return new CanonicalPolicyContent(
                source.endpointCatalog().definitions().stream()
                        .sorted(Comparator.comparing(definition -> definition.endpointId().value()))
                        .map(this::endpoint)
                        .toList(),
                source.channelAccessPolicies().entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().value(),
                        entry -> new CanonicalPolicyContent.ChannelAccess(
                                sorted(entry.getValue().allowedClientAppIds(), ClientAppId::value),
                                entry.getValue().channelCode().value()),
                        (left, right) -> left,
                        TreeMap::new)),
                source.channelCredentialPolicies().entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().value(),
                        entry -> new CanonicalPolicyContent.ChannelCredential(
                                credential(entry.getValue().activeCredential()),
                                entry.getValue().channelCode().value(),
                                entry.getValue().graceCredentialOptional().map(this::credential).orElse(null)),
                        (left, right) -> left,
                        TreeMap::new)),
                source.clientAppPolicies().entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().value(),
                        entry -> clientApp(entry.getValue()),
                        (left, right) -> left,
                        TreeMap::new)),
                source.routeAccessPolicies().entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().value(),
                        entry -> routeAccess(entry.getValue()),
                        (left, right) -> left,
                        TreeMap::new)));
    }

    private CanonicalPolicyContent.Endpoint endpoint(EndpointDefinition source) {
        List<String> matchers = source.matchers().stream()
                .map(matcher -> matcher.httpMethod() + MATCHER_SEPARATOR + matcher.pathPattern())
                .sorted()
                .toList();
        return new CanonicalPolicyContent.Endpoint(
                source.deliveryRouteId(), source.endpointId().value(), matchers, source.ownerService());
    }

    private CanonicalPolicyContent.ClientApp clientApp(IngressClientAppPolicy source) {
        return new CanonicalPolicyContent.ClientApp(
                source.clientAppId().value(), source.status(), source.platform());
    }

    private CanonicalPolicyContent.Credential credential(RequestSignatureChannelCredential source) {
        return new CanonicalPolicyContent.Credential(
                source.channelCode().value(),
                source.credentialId().value(),
                source.expiresAt().toString(),
                source.secretRef(),
                source.signaturePayloadContract(),
                source.validFrom().toString());
    }

    private CanonicalPolicyContent.RouteAccess routeAccess(IngressRouteAccessPolicy source) {
        return new CanonicalPolicyContent.RouteAccess(
                source.channelCredentialRequirement(),
                source.channelRequirement(),
                new CanonicalPolicyContent.Forwarding(
                        sorted(source.credentialForwardingPolicy().allowedCookieNames(), Function.identity()),
                        source.credentialForwardingPolicy().authorizationHeader(),
                        source.credentialForwardingPolicy().cookies()),
                source.enabled(),
                source.endpointId().value(),
                sorted(source.requiredAudiences(), Function.identity()),
                sorted(source.requiredScopes(), Function.identity()),
                source.subjectRequirement());
    }

    private static <T> List<String> sorted(Collection<T> source, Function<T, String> valueMapper) {
        return source.stream().map(valueMapper).sorted().toList();
    }
}
