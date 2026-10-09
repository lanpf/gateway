package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@lombok.RequiredArgsConstructor
public final class DefaultCanonicalPolicyContentMapper implements CanonicalPolicyContentMapper {
    private final CapabilityPolicyConfigStorageMapper mapper;
    private static final String MATCHER_SEPARATOR = " ";

    @Override
    public CanonicalIngressContent toCanonical(IngressContent source) {
        return new CanonicalIngressContent(
                source.endpoints().values().stream()
                        .sorted(Comparator.comparing(definition -> definition.getId().value()))
                        .map(this::endpoint)
                        .toList(),
                source.clients().values().stream().collect(Collectors.toMap(
                        client -> client.getId().value(),
                        this::client,
                        (left, right) -> left,
                        TreeMap::new)),
                source.apps().values().stream().collect(Collectors.toMap(
                        app -> app.getId().value(),
                        this::app,
                        (left, right) -> left,
                        TreeMap::new)),
                mapper.toStored(source.policyConfig()));
    }

    private CanonicalIngressContent.Endpoint endpoint(Endpoint source) {
        List<String> matchers = source.getMatchers().stream()
                .map(matcher -> matcher.httpMethod() + MATCHER_SEPARATOR + matcher.pathPattern())
                .sorted()
                .toList();
        return new CanonicalIngressContent.Endpoint(
                source.getRouteId().value(),
                source.getId().value(),
                matchers,
                source.getServiceId().value(),
                source.getTags().stream().sorted().toList(),
                source.getStatus(),
                endpointAccess(source.getAccess()));
    }

    private CanonicalIngressContent.EndpointAccess endpointAccess(EndpointAccess source) {
        return new CanonicalIngressContent.EndpointAccess(
                source.clientVerificationRequirement(),
                source.subjectAuthenticationRequirement(),
                sorted(source.requiredScopes(), Function.identity()),
                sorted(source.requiredAudiences(), Function.identity()));
    }

    private CanonicalIngressContent.App app(App source) {
        return new CanonicalIngressContent.App(
                source.getId().value(),
                source.getStatus(),
                source.getPlatform(),
                sorted(source.getAccess().allowedOrigins(), Function.identity()));
    }

    private CanonicalIngressContent.Client client(Client source) {
        return new CanonicalIngressContent.Client(
                sorted(source.getAccess().allowedApps(), AppId::value),
                source.getId().value(),
                source.getStatus(),
                credentials(source.getId().value(), source.getAccess().credentials()));
    }

    private CanonicalIngressContent.Credential credential(String clientId, Credential source) {
        return new CanonicalIngressContent.Credential(
                clientId,
                source.credentialId().value(),
                source.expiresAt().toString(),
                source.secretRef(),
                source.signatureContract(),
                source.validFrom().toString());
    }

    private static <T> List<String> sorted(Collection<T> source, Function<T, String> valueMapper) {
        return source.stream().map(valueMapper).sorted().toList();
    }

    private CanonicalIngressContent.ClientCredentials credentials(String clientId, Credentials source) {
        return source == null ? null : new CanonicalIngressContent.ClientCredentials(
                credential(clientId, source.activeCredential()),
                source.graceCredential() == null ? null : credential(clientId, source.graceCredential()));
    }
}
