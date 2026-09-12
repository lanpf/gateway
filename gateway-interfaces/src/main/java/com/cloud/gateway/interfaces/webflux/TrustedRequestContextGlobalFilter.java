package com.cloud.gateway.interfaces.webflux;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.application.ingress.IngressAccessCommand;
import com.cloud.gateway.application.ingress.IngressAccessCommandService;
import com.cloud.gateway.application.ingress.IngressAccessOutput;
import com.cloud.gateway.application.ingress.IngressAdmissionCommandService;
import com.cloud.gateway.api.constants.GatewayPathApiConstants;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.domain.access.IngressAccessDenyReason;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public final class TrustedRequestContextGlobalFilter implements GlobalFilter, Ordered {
    private static final String BROWSER_SESSION_COOKIE = "BROWSER_SESSION";
    private static final String LOCAL_ROUTE_PREFIX = GatewayPathApiConstants.ADMIN_BASE_PATH + "/";
    private static final int MAX_EVIDENCE_BODY_BYTES = 1024 * 1024;

    private final IngressAccessCommandService ingress;
    private final SubjectAuthenticator<Authentication> hostSessionSubjectAuthenticator;
    private final SubjectAuthenticator<String> browserSessionSubjectAuthenticator;
    private final RequestQuotaGuard quotas;
    private final DownstreamPolicyHandler downstream;
    private final RequestHeaderWriter headerWriter = new RequestHeaderWriter();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        IngressAccessSnapshot snapshot = exchange.getAttribute(GatewayRequestAttributes.SNAPSHOT);
        String path = exchange.getRequest().getPath().value();
        Endpoint captured = exchange.getAttribute(GatewayRequestAttributes.ENDPOINT);
        if (snapshot == null || captured == null
                || !Boolean.TRUE.equals(exchange.getAttribute(GatewayRequestAttributes.PREPARATION_COMPLETE))
                || exchange.getAttribute(GatewayRequestAttributes.APPLIED_IP_RULES) == null) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }
        Route delivery = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        boolean local = GatewayRequestAttributes.LOCAL_ROUTE_ID.equals(captured.routeId());
        if ((!local && (delivery == null || !delivery.getId().equals(captured.routeId())))
                || (local && !path.startsWith(LOCAL_ROUTE_PREFIX))) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }
        EndpointPolicy route = snapshot.content().endpointPolicies().get(captured.endpointId());
        if (route == null) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        Mono<CachedRequest> cachedRequest = route.clientVerificationMode() == ClientVerificationMode.REQUEST_SIGNATURE
                || exchange.getRequest().getMethod() == HttpMethod.GET
                ? cacheBody(exchange) : Mono.just(new CachedRequest(exchange, new byte[0]));
        IngressAccessSnapshot requestSnapshot = snapshot;
        return cachedRequest.flatMap(cached -> ReactiveSecurityContextHolder.getContext()
                .map(context -> Optional.ofNullable(context.getAuthentication()))
                .defaultIfEmpty(Optional.empty())
                .flatMap(authentication -> evaluate(cached, chain, requestSnapshot, route, authentication.orElse(null))))
                .onErrorResume(DataBufferLimitException.class, error -> reject(exchange, HttpStatus.PAYLOAD_TOO_LARGE));
    }

    private Mono<Void> evaluate(
            CachedRequest cached,
            GatewayFilterChain chain,
            IngressAccessSnapshot snapshot,
            EndpointPolicy route,
            Authentication authentication
    ) {
        ServerWebExchange exchange = cached.exchange();

        HttpHeaders headers = exchange.getRequest().getHeaders();
        String appIdHeaderValue = headers.getFirst(RequestHeader.APP_ID.headerName());
        String clientIdHeaderValue = headers.getFirst(RequestHeader.CLIENT_ID.headerName());
        AppId appId = StringUtils.hasText(appIdHeaderValue) ? new AppId(appIdHeaderValue) : null;
        ClientId clientId = StringUtils.hasText(clientIdHeaderValue) ? new ClientId(clientIdHeaderValue) : null;
        Endpoint endpoint = exchange.getRequiredAttribute(GatewayRequestAttributes.ENDPOINT);
        IngressAccessCommand command = new IngressAccessCommand(snapshot, endpoint, appId, clientId,
                headers.getFirst(HttpHeaders.ORIGIN), isUnsafe(exchange.getRequest().getMethod()),
                PolicySnapshotWebFilter.remoteIp(exchange));

        Set<String> applied = exchange.getRequiredAttribute(GatewayRequestAttributes.APPLIED_IP_RULES);
        IngressAdmissionCommandService admission = new IngressAdmissionCommandService(ingress, quotas);
        return Mono.deferContextual(reactorContext -> Mono.fromCompletionStage(() -> admission.admit(command,
                signatureEvidence(exchange, cached.payload(), clientId),
                () -> isAuthenticationAbsent(authentication)
                        ? CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent())
                        : hostSessionSubjectAuthenticator.authenticate(authentication).contextWrite(reactorContext).toFuture(),
                () -> {
                    HttpCookie cookie = exchange.getRequest().getCookies().getFirst(BROWSER_SESSION_COOKIE);
                    return cookie == null
                            ? CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent())
                            : browserSessionSubjectAuthenticator.authenticate(cookie.getValue()).contextWrite(reactorContext).toFuture();
                }, applied)))
                .flatMap(admitted -> {
                    IngressAccessOutput output = admitted.access();
                    if (output instanceof IngressAccessOutput.Denied denied) {
                        return reject(exchange, status(denied.decision().reason()));
                    }
                    IngressAccessOutput.Allowed allowed = (IngressAccessOutput.Allowed) output;
                    exchange.getAttributes().put(GatewayRequestAttributes.TRUSTED_CONTEXT, allowed.context());
                    return quotas.respond(exchange, admitted.quota())
                            .flatMap(permitted -> {
                                if (!permitted) {
                                    return Mono.empty();
                                }
                                ServerWebExchange forwarded = headerWriter.write(exchange, allowed.context(),
                                        allowed.headers(), snapshot.content().policies().trustedHeaderNames());
                                if (GatewayRequestAttributes.LOCAL_ROUTE_ID.equals(endpoint.routeId())) {
                                    return chain.filter(forwarded);
                                }
                                return downstream.handle(exchange, forwarded, chain, endpoint,
                                        allowed.context(), allowed.policy());
                            });
                });
    }

    private static ClientSignatureEvidence signatureEvidence(
            ServerWebExchange exchange, byte[] payload, ClientId clientId) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String timestamp = headers.getFirst(RequestHeader.TIMESTAMP.headerName());
        String signature = headers.getFirst(RequestHeader.SIGNATURE.headerName());
        if (clientId == null || !StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            return null;
        }
        return new ClientSignatureEvidence(payload, timestamp, signature,
                exchange.getRequest().getMethod().name(), exchange.getRequest().getURI().getRawPath(),
                exchange.getRequest().getURI().getRawQuery(), headers.getFirst(RequestHeader.APP_ID.headerName()),
                clientId.value(), headers.getFirst(RequestHeader.CREDENTIAL_ID.headerName()),
                headers.getFirst(RequestHeader.NONCE.headerName()));
    }

    Mono<SubjectAuthenticationOutcome> authenticateSubject(
            ServerWebExchange exchange, EndpointPolicy route, Authentication authentication) {
        return Mono.deferContextual(context -> Mono.fromCompletionStage(() -> ingress.authenticateSubject(route.subjectAuthencationRequirement(),
                () -> isAuthenticationAbsent(authentication)
                        ? CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent())
                        : hostSessionSubjectAuthenticator.authenticate(authentication).contextWrite(context).toFuture(),
                () -> {
                    HttpCookie cookie = exchange.getRequest().getCookies().getFirst(BROWSER_SESSION_COOKIE);
                    return cookie == null
                            ? CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent())
                            : browserSessionSubjectAuthenticator.authenticate(cookie.getValue()).contextWrite(context).toFuture();
                })));
    }

    private static boolean isAuthenticationAbsent(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken;
    }

    private static boolean isUnsafe(HttpMethod method) {
        return method == null
                || !(method == HttpMethod.GET
                || method == HttpMethod.HEAD
                || method == HttpMethod.OPTIONS
                || method == HttpMethod.TRACE);
    }

    static HttpStatus status(IngressAccessDenyReason reason) {
        return switch (reason) {
            case APP_NOT_FOUND, CLIENT_REQUIRED -> HttpStatus.BAD_REQUEST;
            case ENDPOINT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CLIENT_VERIFICATION_REQUIRED, CLIENT_VERIFICATION_MISMATCH,
                 SUBJECT_REQUIRED, SUBJECT_AUTHENTICATION_REJECTED, SUBJECT_TYPE_MISMATCH -> HttpStatus.UNAUTHORIZED;
            case CLIENT_VERIFICATION_FAILED,
                 SUBJECT_AUTHENTICATION_UPSTREAM_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case SUBJECT_AUTHENTICATION_RESPONSE_INVALID -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.FORBIDDEN;
        };
    }

    private static Mono<CachedRequest> cacheBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), MAX_EVIDENCE_BODY_BYTES)
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    exchange.getAttributes().put(GatewayRequestAttributes.REQUEST_BODY_PRESENT, bytes.length > 0);
                    ServerHttpRequest decorated = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override public Flux<DataBuffer> getBody() {
                            return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                        }
                    };
                    return new CachedRequest(exchange.mutate().request(decorated).build(), bytes);
                })
                .defaultIfEmpty(new CachedRequest(exchange, new byte[0]));
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 100; }

    private record CachedRequest(ServerWebExchange exchange, byte[] payload) { }
}
