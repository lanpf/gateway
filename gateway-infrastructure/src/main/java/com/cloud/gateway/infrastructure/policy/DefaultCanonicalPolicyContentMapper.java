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
    private final CapabilityCompositionStorageMapper policies;
    private static final String MATCHER_SEPARATOR = " ";

    @Override
    public CanonicalPolicyContent toCanonical(IngressContent source) {
        return new CanonicalPolicyContent(
                source.endpointRegistry().values().stream()
                        .sorted(Comparator.comparing(definition -> definition.endpointId().value()))
                        .map(this::endpoint)
                        .toList(),
                source.clientRegistry().values().stream().collect(Collectors.toMap(
                        client -> client.clientId().value(),
                        this::client,
                        (left, right) -> left,
                        TreeMap::new)),
                source.appRegistry().values().stream().collect(Collectors.toMap(
                        app -> app.appId().value(),
                        this::app,
                        (left, right) -> left,
                        TreeMap::new)),
                policies.toStored(source.capabilityComposition()));
    }

    private CanonicalPolicyContent.Endpoint endpoint(Endpoint source) {
        List<String> matchers = source.matchers().stream()
                .map(matcher -> matcher.httpMethod() + MATCHER_SEPARATOR + matcher.pathPattern())
                .sorted()
                .toList();
        return new CanonicalPolicyContent.Endpoint(
                source.routeId().value(),
                source.endpointId().value(),
                matchers,
                source.serviceId().value(),
                source.tags().stream().sorted().toList(),
                source.status(),
                endpointAccess(source.access()));
    }

    private CanonicalPolicyContent.EndpointAccess endpointAccess(EndpointAccess source) {
        return new CanonicalPolicyContent.EndpointAccess(
                source.clientVerificationRequirement(),
                source.subjectAuthenticationRequirement(),
                sorted(source.requiredScopes(), Function.identity()),
                sorted(source.requiredAudiences(), Function.identity()));
    }

    private CanonicalPolicyContent.App app(App source) {
        return new CanonicalPolicyContent.App(
                source.appId().value(),
                source.status(),
                source.platform(),
                sorted(source.access().allowedOrigins(), Function.identity()));
    }

    private CanonicalPolicyContent.Client client(Client source) {
        return new CanonicalPolicyContent.Client(
                sorted(source.access().allowedApps(), AppId::value),
                source.clientId().value(),
                source.status(),
                credentials(source.clientId().value(), source.access().credentials()));
    }

    private CanonicalPolicyContent.Credential credential(String clientId, Credential source) {
        return new CanonicalPolicyContent.Credential(
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

    private CanonicalPolicyContent.ClientCredentials credentials(String clientId, Credentials source) {
        return source == null ? null : new CanonicalPolicyContent.ClientCredentials(
                credential(clientId, source.activeCredential()),
                source.graceCredential() == null ? null : credential(clientId, source.graceCredential()));
    }
}
