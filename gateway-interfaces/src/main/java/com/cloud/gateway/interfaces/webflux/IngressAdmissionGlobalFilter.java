package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.interfaces.subject.SubjectAuthenticator;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.application.ingress.AccessStageCommand;
import com.cloud.gateway.application.ingress.PreparationStageOutput;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.application.ingress.AccessStageOutput;
import com.cloud.gateway.application.ingress.AdmissionStageOutput;
import com.cloud.gateway.application.ingress.CapabilityExecutionCommandService;
import com.cloud.gateway.application.ingress.CapabilityExecutionOutput;
import com.cloud.gateway.domain.capability.runtime.CapabilityContext;
import com.cloud.gateway.domain.capability.runtime.CapabilitySlot;
import com.cloud.gateway.domain.capability.runtime.HeaderApplyCapability;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.runtime.TimeoutCapability;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.application.ingress.IngressAdmissionCommandService;
import com.cloud.gateway.application.ingress.IngressAdmissionOutput;
import com.cloud.gateway.api.constants.GatewayPathApiConstants;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.domain.ingress.access.AccessDenyReason;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.client.ClientId;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

@RequiredArgsConstructor
public final class IngressAdmissionGlobalFilter implements GlobalFilter, Ordered {
    private static final String BROWSER_SESSION_COOKIE = "BROWSER_SESSION";
    private static final String LOCAL_ROUTE_PREFIX = GatewayPathApiConstants.ADMIN_BASE_PATH + "/";
    private static final int MAX_EVIDENCE_BODY_BYTES = 1024 * 1024;

    private final IngressAdmissionCommandService ingress;
    private final SubjectAuthenticator<Authentication> hostSessionSubjectAuthenticator;
    private final SubjectAuthenticator<String> browserSessionSubjectAuthenticator;
    private final CapabilityResponseWriter responses;
    private final CapabilityExecutionCommandService capabilities;
    private final RequestHeaderWriter headerWriter = new RequestHeaderWriter();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        PreparationStageOutput.Prepared prepared = exchange.getAttribute(GatewayRequestAttributes.PREPARED);
        if (prepared == null
                || !Boolean.TRUE.equals(exchange.getAttribute(GatewayRequestAttributes.PREPARATION_COMPLETE))) {
            return reject(exchange, HttpStatus.NOT_FOUND);
        }
        Endpoint captured = exchange.getAttribute(GatewayRequestAttributes.ENDPOINT);
        if (captured != null) {
            String routeId = captured.routeId().value();
            Route delivery = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            boolean local = GatewayRequestAttributes.LOCAL_ROUTE_ID.equals(routeId);
            if (local
                    ? !exchange.getRequest().getPath().value().startsWith(LOCAL_ROUTE_PREFIX)
                    : delivery == null || !delivery.getId().equals(routeId)) {
                return reject(exchange, HttpStatus.NOT_FOUND);
            }
        }
        EndpointAccess route = captured == null ? null : captured.access();

        boolean signatureEvidenceSubmitted = StringUtils.hasText(
                exchange.getRequest().getHeaders().getFirst(RequestHeader.TIMESTAMP.headerName()))
                && StringUtils.hasText(exchange.getRequest().getHeaders().getFirst(RequestHeader.SIGNATURE.headerName()));
        Mono<CachedRequest> cachedRequest = exchange.getRequest().getMethod() == HttpMethod.GET
                || (route != null && route.clientVerificationRequirement() == ClientVerificationRequirement.REQUIRED)
                || signatureEvidenceSubmitted
                ? cacheBody(exchange) : Mono.just(new CachedRequest(exchange, new byte[0]));
        return cachedRequest.flatMap(cached -> ReactiveSecurityContextHolder.getContext()
                .map(context -> Optional.ofNullable(context.getAuthentication()))
                .defaultIfEmpty(Optional.empty())
                .flatMap(authentication -> evaluate(cached, chain, prepared, authentication.orElse(null))))
                .onErrorResume(DataBufferLimitException.class, error -> reject(exchange, HttpStatus.PAYLOAD_TOO_LARGE));
    }

    private Mono<Void> evaluate(
            CachedRequest cached,
            GatewayFilterChain chain,
            PreparationStageOutput.Prepared prepared,
            Authentication authentication
    ) {
        ServerWebExchange exchange = cached.exchange();

        HttpHeaders headers = exchange.getRequest().getHeaders();
        String clientIdHeaderValue = headers.getFirst(RequestHeader.CLIENT_ID.headerName());
        ClientId clientId = StringUtils.hasText(clientIdHeaderValue) ? new ClientId(clientIdHeaderValue) : null;
        AccessStageCommand command = new AccessStageCommand(prepared,
                signatureEvidence(exchange, cached.payload(), clientId));

        return Mono.deferContextual(reactorContext -> Mono.fromCompletionStage(() -> ingress.admit(command,
                subjectAuthentication(exchange, authentication, reactorContext))))
                .flatMap(output -> {
                    if (output instanceof IngressAdmissionOutput.AccessDenied denied) {
                        return reject(exchange, status(denied.access().decision().denyReason()));
                    }
                    AdmissionStageOutput admitted = ((IngressAdmissionOutput.Evaluated) output).admission();
                    AccessStageOutput.Allowed allowed = admitted.access();
                    exchange.getAttributes().put(GatewayRequestAttributes.TRUSTED_CONTEXT, allowed.decision().grantedContext());
                    return responses.respond(exchange, admitted.execution())
                            .flatMap(permitted -> permitted
                                    ? forward(exchange, chain, prepared, allowed, admitted)
                                    : Mono.empty());
                });
    }

    /**
     * The FORWARD phase: capabilities that shape the upstream call run once, after admission and
     * before it is issued. Two of them only declare what to do -- the timeout budget and the header
     * plan -- so the phase hands those values over and this adapter applies them.
     *
     * <p>The header plan is applied here rather than before the phase, so that a refused call is never
     * rewritten. Transport failures are projected here without an additional capability phase.
     */
    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain,
            PreparationStageOutput.Prepared prepared, AccessStageOutput.Allowed allowed,
            AdmissionStageOutput admitted) {
        Endpoint endpoint = ((EndpointMatch.Unique) prepared.endpointMatch()).endpoint();
        AccessGrantedContext granted = allowed.decision().grantedContext();
        CapabilityContext context = new CapabilityContext(CapabilitySlot.FORWARD, prepared.evidence(), endpoint,
                granted);
        // Admission already resolved against this same snapshot, endpoint and granted identity, and
        // resolution does not read the slot -- so reusing it is not a shortcut
        // that could resolve differently, it is simply not repeating work that cannot differ.
        return Mono.fromCompletionStage(() -> capabilities.executeAgainst(admitted.capabilities(), context,
                admitted.execution().executed())).flatMap(output ->
                responses.respond(exchange, output.execution()).flatMap(permitted -> {
                    if (!permitted) {
                        return Mono.empty();
                    }
                    HeaderConfig plan = headerPlan(output);
                    if (plan == null) {
                        // Fail closed. Forwarding without the plan would send the caller's own protected
                        // fields downstream, so a missing plan is a defect to stop on, never to skip.
                        // Unreachable today -- the resolver always supplies a plan -- but this is the
                        // guard that keeps a future change from turning that into a silent leak.
                        return reject(exchange, HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                    ServerWebExchange forwarded = headerWriter.write(exchange, granted, plan);
                    TimeoutConfig timeout = timeoutPolicy(output);
                    if (timeout != null) {
                        // Connect and read belong to the transport, so they are published to the route
                        // metadata the client reads; the total is enforced below, around the whole call.
                        RouteTimeoutMetadata.apply(forwarded, timeout);
                    }
                    Mono<Void> downstream = chain.filter(forwarded);
                    if (timeout != null) {
                        downstream = downstream.timeout(timeout.total());
                    }
                    return downstream.onErrorResume(error -> onDownstreamFailure(forwarded, error));
                }));
    }

    private static HeaderConfig headerPlan(CapabilityExecutionOutput output) {
        return output.execution().outcomes().stream()
                .filter(HeaderApplyCapability.Outcome.class::isInstance)
                .map(HeaderApplyCapability.Outcome.class::cast)
                .map(HeaderApplyCapability.Outcome::policy)
                .map(com.cloud.gateway.domain.capability.header.EffectiveHeaderPolicy::config)
                .findFirst().orElse(null);
    }

    private static TimeoutConfig timeoutPolicy(CapabilityExecutionOutput output) {
        return output.execution().outcomes().stream()
                .filter(TimeoutCapability.Outcome.class::isInstance)
                .map(TimeoutCapability.Outcome.class::cast)
                .map(TimeoutCapability.Outcome::policy)
                .map(com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy::config)
                .findFirst().orElse(null);
    }

    /**
     * Project supported transport failures only before commitment; never replace a committed response.
     */
    private static Mono<Void> onDownstreamFailure(ServerWebExchange exchange, Throwable error) {
        HttpStatus status = failureStatus(error);
        if (status == null || exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        return reject(exchange, status);
    }

    /** Map known transport errors; unrelated failures retain their original error handling. */
    private static HttpStatus failureStatus(Throwable error) {
        if (error instanceof TimeoutException || error instanceof io.netty.handler.timeout.TimeoutException
                || error instanceof ResponseStatusException status
                        && status.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        if (error instanceof IOException || error instanceof PrematureCloseException) {
            return HttpStatus.BAD_GATEWAY;
        }
        return null;
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

    /** Transport selection by request evidence: Bearer token → host, BROWSER_SESSION cookie → browser. */
    private Supplier<CompletionStage<SubjectAuthenticationOutcome>> subjectAuthentication(
            ServerWebExchange exchange, Authentication authentication,
            reactor.util.context.ContextView reactorContext) {
        return () -> {
            if (!isAuthenticationAbsent(authentication)) {
                return hostSessionSubjectAuthenticator.authenticate(authentication)
                        .contextWrite(reactorContext).toFuture();
            }
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst(BROWSER_SESSION_COOKIE);
            if (cookie != null) {
                return browserSessionSubjectAuthenticator.authenticate(cookie.getValue())
                        .contextWrite(reactorContext).toFuture();
            }
            return CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent());
        };
    }

    private static boolean isAuthenticationAbsent(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken;
    }

    static HttpStatus status(AccessDenyReason reason) {
        return switch (reason) {
            case APP_NOT_FOUND, CLIENT_REQUIRED -> HttpStatus.BAD_REQUEST;
            case ENDPOINT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CLIENT_VERIFICATION_REQUIRED, CLIENT_VERIFICATION_MISMATCH,
                 SUBJECT_REQUIRED, SUBJECT_AUTHENTICATION_REJECTED -> HttpStatus.UNAUTHORIZED;
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
