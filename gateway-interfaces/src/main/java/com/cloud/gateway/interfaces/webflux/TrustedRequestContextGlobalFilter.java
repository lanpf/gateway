package com.cloud.gateway.interfaces.webflux;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.security.ChannelCredentialAuthenticator;
import com.cloud.gateway.application.security.ChannelSignatureEvidence;
import com.cloud.gateway.domain.access.ChannelCredentialAuthenticationOutcome;
import com.cloud.gateway.domain.access.IngressAccessDecision;
import com.cloud.gateway.domain.access.IngressAccessDenyReason;
import com.cloud.gateway.domain.access.IngressAccessPolicyEvaluator;
import com.cloud.gateway.domain.access.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.TrustedRequestContext;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.policy.AuthorizationHeaderPolicy;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.CredentialForwardingPolicy;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.ClientAppId;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
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

import java.time.Clock;
import java.util.Optional;

@RequiredArgsConstructor
public final class TrustedRequestContextGlobalFilter implements GlobalFilter, Ordered {
    private static final int MAX_CLIENT_VERSION_LENGTH = 64;
    private static final String BROWSER_SESSION_COOKIE = "BROWSER_SESSION";

    private final PolicySnapshotProvider snapshotProvider;
    private final ChannelCredentialAuthenticator channelCredentialAuthenticator;
    private final SubjectAuthenticator<Authentication> hostSessionSubjectAuthenticator;
    private final SubjectAuthenticator<String> browserSessionSubjectAuthenticator;
    private final IngressAccessPolicyEvaluator evaluator;
    private final Clock clock;

    private final SnapshotEndpointResolver endpointResolver = new SnapshotEndpointResolver();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        IngressPolicySnapshot snapshot = snapshotProvider.current().snapshot();
        String httpMethod = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        Optional<EndpointDefinition> endpoint = endpointResolver.resolve(snapshot.content().endpointCatalog(), httpMethod, path);
        if (endpoint.isEmpty()) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }
        IngressRouteAccessPolicy route = snapshot.content().routeAccessPolicies().get(endpoint.get().endpointId());
        if (route == null) {
            return reject(exchange, HttpStatus.FORBIDDEN);
        }

        Mono<CachedRequest> cachedRequest = route.channelCredentialRequirement() == ChannelCredentialRequirement.REQUEST_SIGNATURE
                ? cacheBody(exchange) : Mono.just(new CachedRequest(exchange, new byte[0]));
        return cachedRequest.flatMap(cached -> ReactiveSecurityContextHolder.getContext()
                .map(context -> Optional.ofNullable(context.getAuthentication()))
                .defaultIfEmpty(Optional.empty())
                .flatMap(authentication -> evaluate(cached, chain, snapshot, route, authentication.orElse(null))));
    }

    private Mono<Void> evaluate(
            CachedRequest cached,
            GatewayFilterChain chain,
            IngressPolicySnapshot snapshot,
            IngressRouteAccessPolicy route,
            Authentication authentication
    ) {
        ServerWebExchange exchange = cached.exchange();

        HttpHeaders headers = exchange.getRequest().getHeaders();
        String clientAppIdHeaderValue = headers.getFirst(RequestHeader.CLIENT_APP_ID);
        String channelCodeHeaderValue = headers.getFirst(RequestHeader.CHANNEL_CODE);
        ClientAppId clientAppId = StringUtils.hasText(clientAppIdHeaderValue) ? new ClientAppId(clientAppIdHeaderValue) : null;
        ChannelCode channelCode = StringUtils.hasText(channelCodeHeaderValue) ? new ChannelCode(channelCodeHeaderValue) : null;
        IngressClientAppPolicy clientApp = clientAppId == null
                ? null : snapshot.content().clientAppPolicies().get(clientAppId);
        IngressChannelAccessPolicy channelAccess = channelCode == null
                ? null : snapshot.content().channelAccessPolicies().get(channelCode);

        Mono<ChannelCredentialAuthenticationOutcome> channelCredentialAuthentication =
                authenticateChannelCredential(exchange, cached.payload(), channelCode, route, snapshot);
        Mono<SubjectAuthenticationOutcome> subjectAuthentication =
                authenticateSubject(exchange, route, authentication);

        return Mono.zip(channelCredentialAuthentication, subjectAuthentication)
                .flatMap(authentications -> {
                    IngressAccessDecision decision = evaluator.evaluate(
                            snapshot.snapshotVersion(), channelCode,
                            clientApp, channelAccess, route,
                            exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN),
                            isUnsafe(exchange.getRequest().getMethod()),
                            authentications.getT1(), authentications.getT2());
                    if (decision instanceof IngressAccessDecision.Denied denied) {
                        return reject(exchange, status(denied.reason()));
                    }
                    TrustedRequestContext context =
                            ((IngressAccessDecision.Allowed) decision).trustedRequestContext();
                    return chain.filter(forward(exchange, context, route));
                });
    }

    private Mono<ChannelCredentialAuthenticationOutcome> authenticateChannelCredential(
            ServerWebExchange exchange,
            byte[] payload,
            ChannelCode channelCode,
            IngressRouteAccessPolicy route,
            IngressPolicySnapshot snapshot
    ) {
        if (route.channelCredentialRequirement() != ChannelCredentialRequirement.REQUEST_SIGNATURE
                || channelCode == null) {
            return Mono.just(ChannelCredentialAuthenticationOutcome.absent());
        }
        IngressChannelCredentialPolicy channelCredential = snapshot.content().channelCredentialPolicies().get(channelCode);
        if (channelCredential == null) {
            return Mono.just(ChannelCredentialAuthenticationOutcome.rejected());
        }
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String timestampHeaderValue = headers.getFirst(GatewayRequestHeaders.TIMESTAMP);
        String signatureHeaderValue = headers.getFirst(GatewayRequestHeaders.SIGNATURE);
        if (!StringUtils.hasText(timestampHeaderValue) || !StringUtils.hasText(signatureHeaderValue)) {
            return Mono.just(ChannelCredentialAuthenticationOutcome.absent());
        }
        try {
            return Mono.fromCompletionStage(channelCredentialAuthenticator.authenticate(
                    channelCredential,
                    new ChannelSignatureEvidence(payload, timestampHeaderValue, signatureHeaderValue),
                    clock.instant()));
        } catch (IllegalArgumentException exception) {
            return Mono.just(ChannelCredentialAuthenticationOutcome.rejected());
        }
    }

    Mono<SubjectAuthenticationOutcome> authenticateSubject(
            ServerWebExchange exchange, IngressRouteAccessPolicy route, Authentication authentication) {
        return switch (route.subjectRequirement()) {
            case IGNORE -> Mono.just(SubjectAuthenticationOutcome.absent());
            case BROWSER_SESSION_REQUIRED -> {
                HttpCookie cookie = exchange.getRequest().getCookies().getFirst(BROWSER_SESSION_COOKIE);
                yield cookie == null
                        ? Mono.just(SubjectAuthenticationOutcome.absent())
                        : browserSessionSubjectAuthenticator.authenticate(cookie.getValue());
            }
            case HOST_SESSION_REQUIRED, HOST_SESSION_OPTIONAL -> isAuthenticationAbsent(authentication)
                    ? Mono.just(SubjectAuthenticationOutcome.absent())
                    : hostSessionSubjectAuthenticator.authenticate(authentication);
        };
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

    private static ServerWebExchange forward(
            ServerWebExchange exchange, TrustedRequestContext context, IngressRouteAccessPolicy route) {
        String declaredVersion = sanitizeClientVersion(
                exchange.getRequest().getHeaders().getFirst(RequestHeader.CLIENT_VERSION));
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            stripProtectedHeaders(headers);
            headers.set(RequestHeader.CLIENT_APP_ID, context.clientContext().clientAppId().value());
            headers.set(RequestHeader.CLIENT_PLATFORM, context.clientContext().platform());
            setIfPresent(headers, RequestHeader.CLIENT_VERSION, declaredVersion);
            if (context.channelContext() != null) {
                headers.set(RequestHeader.CHANNEL_CODE, context.channelContext().channelCode().value());
            }
            if (context.subjectContext() != null) {
                headers.set(RequestHeader.SUBJECT_TYPE, context.subjectContext().subjectType().name());
                headers.set(RequestHeader.USER_ID, context.subjectContext().subjectId());
                headers.set(RequestHeader.SESSION_ID, context.subjectContext().loginSessionId());
            }
            if (route.credentialForwardingPolicy().authorizationHeader() == AuthorizationHeaderPolicy.STRIP) {
                headers.remove(HttpHeaders.AUTHORIZATION);
            }
            applyCookiePolicy(exchange, headers, route);
        }).build();
        return exchange.mutate().request(request).build();
    }

    private static void applyCookiePolicy(
            ServerWebExchange exchange, HttpHeaders headers, IngressRouteAccessPolicy route) {
        CredentialForwardingPolicy forwarding = route.credentialForwardingPolicy();
        headers.remove(HttpHeaders.COOKIE);
        if (forwarding.cookies() != CookieForwardingMode.ALLOW_LIST) return;
        forwarding.allowedCookieNames().forEach(name -> exchange.getRequest().getCookies().getOrDefault(name, java.util.List.of())
                .forEach(cookie -> headers.add(HttpHeaders.COOKIE, cookie.toString())));
    }

    private static void stripProtectedHeaders(HttpHeaders headers) {
        headers.remove(RequestHeader.CLIENT_APP_ID);
        headers.remove(RequestHeader.CLIENT_PLATFORM);
        headers.remove(RequestHeader.CLIENT_VERSION);
        headers.remove(RequestHeader.CHANNEL_CODE);
        headers.remove(RequestHeader.USER_ID);
        headers.remove(RequestHeader.SESSION_ID);
        headers.remove(RequestHeader.SUBJECT_TYPE);
    }

    private static void setIfPresent(HttpHeaders headers, String name, String value) {
        if (StringUtils.hasText(value)) {
            headers.set(name, value);
        }
    }

    private static String sanitizeClientVersion(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_CLIENT_VERSION_LENGTH || !trimmed.matches("[A-Za-z0-9._+-]+")) {
            return null;
        }
        return trimmed;
    }

    static HttpStatus status(IngressAccessDenyReason reason) {
        return switch (reason) {
            case CLIENT_APP_NOT_FOUND, CHANNEL_REQUIRED -> HttpStatus.BAD_REQUEST;
            case ENDPOINT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SUBJECT_REQUIRED, SUBJECT_AUTHENTICATION_FAILED, SUBJECT_TYPE_MISMATCH -> HttpStatus.UNAUTHORIZED;
            case SUBJECT_AUTHENTICATION_DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case SUBJECT_AUTHENTICATION_RESPONSE_INVALID -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.FORBIDDEN;
        };
    }

    private static Mono<CachedRequest> cacheBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
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
