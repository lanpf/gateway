package com.cloud.gateway.interfaces.webflux;

import com.cloud.framework.core.RequestHeader;

import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.ingress.IngressPreparationCommand;
import com.cloud.gateway.application.ingress.IngressPreparationCommandService;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.Endpoint;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.net.InetSocketAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Captures policy before security, including requests without an SCG route. */
@RequiredArgsConstructor
public final class PolicySnapshotWebFilter implements WebFilter, Ordered {
    private final PolicySnapshotProvider provider;
    private final RequestQuotaGuard quotas;

    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            IngressPreparationCommandService preparation = new IngressPreparationCommandService(
                    provider, new SnapshotEndpointResolver(), quotas);
            return Mono.fromCompletionStage(() -> preparation.prepare(new IngressPreparationCommand(
                    exchange.getRequest().getMethod().name(), exchange.getRequest().getPath().value(), remoteIp(exchange))))
                    .flatMap(prepared -> {
                        exchange.getAttributes().put(GatewayRequestAttributes.SNAPSHOT, prepared.snapshot());
                        Endpoint endpoint = prepared.endpoint();
                        if (endpoint != null) {
                            exchange.getAttributes().put(GatewayRequestAttributes.ENDPOINT, endpoint);
                        }
                        exchange.getAttributes().put(GatewayRequestAttributes.APPLIED_IP_RULES,
                                new HashSet<>(prepared.appliedRules()));
                        return quotas.respond(exchange, prepared.quota())
                    .flatMap(allowed -> {
                        if (!allowed) {
                            return Mono.empty();
                        }
                        String path = exchange.getRequest().getPath().value();
                        if (path.equals("/internal") || path.startsWith("/internal/")) {
                            return reject(exchange, HttpStatus.NOT_FOUND);
                        }
                        if (endpoint == null) {
                            return reject(exchange, HttpStatus.NOT_FOUND);
                        }
                        for (String name : Set.of(RequestHeader.APP_ID.headerName(), RequestHeader.CLIENT_ID.headerName(),
                                RequestHeader.SIGNATURE.headerName(), RequestHeader.TIMESTAMP.headerName(),
                                RequestHeader.CREDENTIAL_ID.headerName(), RequestHeader.NONCE.headerName())) {
                            List<String> values = exchange.getRequest().getHeaders().get(name);
                            if (values != null && (values.size() != 1 || values.get(0).length() > 4096)) {
                                return reject(exchange, HttpStatus.BAD_REQUEST);
                            }
                        }
                        exchange.getAttributes().put(GatewayRequestAttributes.PREPARATION_COMPLETE, true);
                        return chain.filter(exchange);
                    });
                    });
        }).onErrorResume(DomainException.class, error -> reject(exchange, HttpStatus.BAD_REQUEST));
    }

    static String remoteIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }
}
