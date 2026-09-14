package com.cloud.gateway.interfaces.security;

import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.security.SubjectAuthenticationSelection;
import com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Spring binding only; authentication-family selection belongs to the domain. */
public final class PolicyAwareBearerTokenConverter implements ServerAuthenticationConverter {
    private final ServerBearerTokenAuthenticationConverter delegate = new ServerBearerTokenAuthenticationConverter();

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        IngressAccessSnapshot snapshot = exchange.getAttribute(GatewayRequestAttributes.SNAPSHOT);
        Endpoint endpoint = exchange.getAttribute(GatewayRequestAttributes.ENDPOINT);
        if (snapshot != null && endpoint != null) {
            EndpointAccess policy = endpoint.access();
            if (SubjectAuthenticationSelection.from(policy.subjectAuthenticationRequirement())
                    != SubjectAuthenticationSelection.HOST) {
                return Mono.empty();
            }
        }
        return delegate.convert(exchange);
    }
}
