package com.cloud.gateway.interfaces.webflux;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.application.access.publication.AccessSnapshotProvider;
import com.cloud.gateway.application.ingress.IngressPreparationCommandService;
import com.cloud.gateway.application.ingress.IngressPreparationOutput;
import com.cloud.gateway.application.ingress.PrepareIngressCommand;
import com.cloud.gateway.domain.access.RequestEvidence;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Stage one at the edge: captures the snapshot and evidence, executes the pre-authentication IP quota. */
@RequiredArgsConstructor
public final class AccessSnapshotWebFilter implements WebFilter, Ordered {
    private final com.cloud.gateway.application.access.publication.AccessSnapshotProvider provider;
    private final RequestQuotaGuard quotas;

    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            IngressPreparationCommandService preparation = new IngressPreparationCommandService(
                    provider, new SnapshotEndpointMatchSource(), quotas, () -> { });
            return Mono.fromCompletionStage(() -> preparation.prepare(new PrepareIngressCommand(
                            evidence(exchange))))
                    .flatMap(output -> {
                        if (output instanceof IngressPreparationOutput.Denied denied) {
                            return quotas.respond(exchange, denied.decision()).then(Mono.empty());
                        }
                        IngressPreparationOutput.Prepared prepared = (IngressPreparationOutput.Prepared) output;
                        exchange.getAttributes().put(GatewayRequestAttributes.SNAPSHOT, prepared.snapshot());
                        exchange.getAttributes().put(GatewayRequestAttributes.EVIDENCE, prepared.evidence());
                        if (prepared.endpointMatch() instanceof EndpointMatch.Unique unique) {
                            exchange.getAttributes().put(GatewayRequestAttributes.ENDPOINT, unique.endpoint());
                        }
                        exchange.getAttributes().put(GatewayRequestAttributes.APPLIED_IP_RULES,
                                prepared.quota().executedRuleIds());
                        return quotas.respond(exchange, prepared.quota().outcome()).flatMap(allowed -> {
                            if (!allowed) {
                                return Mono.empty();
                            }
                            HttpStatus rejection = edgeRejection(exchange);
                            return rejection == null ? chain.filter(exchange) : reject(exchange, rejection);
                        });
                    });
        }).onErrorResume(DomainException.class, error -> reject(exchange, HttpStatus.BAD_REQUEST));
    }

    /** Internal paths and malformed declaration headers never reach the security chain. */
    private static HttpStatus edgeRejection(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        if (path.equals("/internal") || path.startsWith("/internal/")) {
            return HttpStatus.NOT_FOUND;
        }
        for (String name : Set.of(RequestHeader.APP_ID.headerName(), RequestHeader.CLIENT_ID.headerName(),
                RequestHeader.SIGNATURE.headerName(), RequestHeader.TIMESTAMP.headerName(),
                RequestHeader.CREDENTIAL_ID.headerName(), RequestHeader.NONCE.headerName())) {
            List<String> values = exchange.getRequest().getHeaders().get(name);
            if (values != null && (values.size() != 1 || values.get(0).length() > 4096)) {
                return HttpStatus.BAD_REQUEST;
            }
        }
        return null;
    }

    private static RequestEvidence evidence(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        String appId = headers.getFirst(RequestHeader.APP_ID.headerName());
        String clientId = headers.getFirst(RequestHeader.CLIENT_ID.headerName());
        return new RequestEvidence(request.getMethod().name(), request.getPath().value(),
                request.getURI().getRawQuery(),
                appId == null ? null : new com.cloud.gateway.domain.app.AppId(appId),
                clientId == null ? null : new com.cloud.gateway.domain.client.ClientId(clientId),
                headers.getFirst(HttpHeaders.ORIGIN), remoteIp(exchange));
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
