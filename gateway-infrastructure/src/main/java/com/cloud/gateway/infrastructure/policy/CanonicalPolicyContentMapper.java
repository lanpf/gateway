package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CanonicalPolicyContentMapper {
    private final StoredPolicySnapshotMapper storageMapper = new StoredPolicySnapshotMapper();
    private static final String MATCHER_SEPARATOR = " ";

    public CanonicalPolicyContent toCanonical(IngressAccessContent source) {
        return new CanonicalPolicyContent(
                source.endpointRegistry().definitions().stream()
                        .sorted(Comparator.comparing(definition -> definition.endpointId().value()))
                        .map(this::endpoint)
                        .toList(),
                source.clients().entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().value(),
                        entry -> new CanonicalPolicyContent.Client(
                                sorted(entry.getValue().allowedApps(), AppId::value),
                                entry.getValue().clientId().value(), entry.getValue().status(),
                                credentials(entry.getValue().credentials())),
                        (left, right) -> left,
                        TreeMap::new)),
                source.appPolicies().entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().value(),
                        entry -> app(entry.getValue()),
                        (left, right) -> left,
                        TreeMap::new)),
                source.endpointPolicies().entrySet().stream().collect(Collectors.toMap(
                        entry -> entry.getKey().value(),
                        entry -> endpointAccess(entry.getValue()),
                        (left, right) -> left,
                        TreeMap::new)), storageMapper.toStoredPolicies(source.policies()));
    }

    private CanonicalPolicyContent.Endpoint endpoint(Endpoint source) {
        List<String> matchers = source.matchers().stream()
                .map(matcher -> matcher.httpMethod() + MATCHER_SEPARATOR + matcher.pathPattern())
                .sorted()
                .toList();
        return new CanonicalPolicyContent.Endpoint(
                source.routeId(), source.endpointId().value(), matchers, source.serviceId(), source.tags().stream().sorted().toList());
    }

    private CanonicalPolicyContent.App app(AppPolicy source) {
        return new CanonicalPolicyContent.App(
                source.appId().value(),
                source.status(),
                source.platform(),
                sorted(source.allowedOrigins(), Function.identity()));
    }

    private CanonicalPolicyContent.Credential credential(Credential source) {
        return new CanonicalPolicyContent.Credential(
                source.clientId().value(),
                source.credentialId().value(),
                source.expiresAt().toString(),
                source.secretRef(),
                source.signatureContract(),
                source.validFrom().toString());
    }

    private CanonicalPolicyContent.EndpointAccess endpointAccess(EndpointPolicy source) {
        return new CanonicalPolicyContent.EndpointAccess(
                source.clientVerificationMode(),
                source.clientVerificationRequirement(),
                new CanonicalPolicyContent.Forwarding(
                        sorted(source.credentialForwarding().allowedCookieNames(), Function.identity()),
                        source.credentialForwarding().authorizationHeader(),
                        source.credentialForwarding().cookies()),
                source.status(),
                source.endpointId().value(),
                sorted(source.requiredAudiences(), Function.identity()),
                sorted(source.requiredScopes(), Function.identity()),
                source.subjectAuthencationRequirement());
    }

    private static <T> List<String> sorted(Collection<T> source, Function<T, String> valueMapper) {
        return source.stream().map(valueMapper).sorted().toList();
    }

    private CanonicalPolicyContent.ClientCredentials credentials(Credentials source) {
        return source == null ? null : new CanonicalPolicyContent.ClientCredentials(
                credential(source.activeCredential()),
                source.graceCredential() == null ? null : credential(source.graceCredential()));
    }
}
