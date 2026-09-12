package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.governance.CachedRepresentation;
import com.cloud.gateway.application.governance.DownstreamExecution;
import com.cloud.gateway.application.governance.ResponseCache;
import com.cloud.gateway.domain.policy.bundle.EffectivePolicy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

/** One HTTP attempt: bounded capture, transport timeout, cancellation, and commit-safe replacement. */
final class DownstreamResponseLifecycle extends ServerHttpResponseDecorator {
    private final ServerWebExchange exchange;
    private final GatewayResponseWriter writer;
    private final DownstreamExecution execution;
    private final EffectivePolicy policy;
    private final long started;

    DownstreamResponseLifecycle(ServerWebExchange exchange, GatewayResponseWriter writer,
            DownstreamExecution execution, EffectivePolicy policy) {
        super(exchange.getResponse());
        this.exchange = exchange;
        this.writer = writer;
        this.execution = execution;
        this.policy = policy;
        this.started = System.nanoTime();
    }

    Mono<Void> execute(GatewayFilterChain chain) {
        Mono<Void> attempt = Mono.defer(() -> {
            applyTimeoutMetadata();
            return chain.filter(exchange.mutate().response(this).build());
        });
        if (policy.timeout() != null) {
            attempt = attempt.timeout(policy.timeout().total());
        }
        return attempt.onErrorResume(this::onError).doFinally(signal -> execution.finish());
    }

    private Mono<Void> onError(Throwable error) {
        ResponseCache.UpstreamFailure classified = !isCommitted() ? classify(error) : null;
        Optional<ResponseCache.UpstreamFailure> failure = execution.fail(classified);
        if (failure.isEmpty()) {
            if (classified == ResponseCache.UpstreamFailure.TIMEOUT) {
                disposeOrigin();
                return writer.syntheticError(HttpStatus.GATEWAY_TIMEOUT);
            }
            return Mono.error(error);
        }
        disposeOrigin();
        return fallback(failure.orElseThrow(), failure.orElseThrow() == ResponseCache.UpstreamFailure.TIMEOUT
                ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY);
    }

    Mono<Void> fallback(ResponseCache.UpstreamFailure failure, HttpStatus status) {
        return Mono.defer(() -> {
            // Recheck absolute expiry after origin cancellation, immediately before producing fallback.
            Optional<CachedRepresentation> stale = execution.stale(failure);
            return stale.isPresent() ? writer.write(stale.orElseThrow()) : writer.syntheticError(status);
        });
    }

    private int recordHeaders() {
        int status = status();
        execution.observeHeaders(status);
        return status;
    }

    private int status() {
        return getStatusCode() == null ? HttpStatus.OK.value() : getStatusCode().value();
    }

    private boolean canFallback(int status) {
        return !isCommitted() && execution.canFallback(status);
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        return Mono.defer(() -> {
            int status = recordHeaders();
            if (canFallback(status)) {
                disposeOrigin();
                return cancelBody(body).then(fallback(ResponseCache.UpstreamFailure.SERVER_ERROR, HttpStatus.valueOf(status)));
            }
            if (!execution.canCapture(status)) {
                return super.writeWith(body);
            }
            ByteArrayOutputStream collected = new ByteArrayOutputStream();
            AtomicBoolean overflow = new AtomicBoolean();
            return super.writeWith(Flux.from(body).doOnNext(buffer -> {
                if (overflow.get()) {
                    return;
                }
                if ((long) collected.size() + buffer.readableByteCount() > policy.cache().maxBodyBytes()) {
                    overflow.set(true);
                    collected.reset();
                } else {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.asByteBuffer().get(bytes);
                    collected.writeBytes(bytes);
                }
            })).doOnSuccess(ignored -> {
                // Inspect committed metadata, including beforeCommit changes and separate cookies storage.
                if (!overflow.get()) {
                    execution.store(new CachedRepresentation(status(), getHeaders(), collected.toByteArray(),
                                    !getCookies().isEmpty()),
                            Duration.ofNanos(System.nanoTime() - started));
                }
            });
        });
    }

    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
        return Mono.defer(() -> {
            int status = recordHeaders();
            if (canFallback(status)) {
                disposeOrigin();
                return cancelBody(Flux.from(body).concatMap(Flux::from))
                        .then(fallback(ResponseCache.UpstreamFailure.SERVER_ERROR, HttpStatus.valueOf(status)));
            }
            return super.writeAndFlushWith(body);
        });
    }

    @Override
    public Mono<Void> setComplete() {
        return Mono.defer(() -> {
            int status = recordHeaders();
            if (canFallback(status)) {
                disposeOrigin();
                return fallback(ResponseCache.UpstreamFailure.SERVER_ERROR, HttpStatus.valueOf(status));
            }
            return super.setComplete();
        });
    }

    private static Mono<Void> cancelBody(Publisher<? extends DataBuffer> body) {
        // limitRequest=false subscribes and cancels; take(0) alone never subscribes in Reactor 3.7.
        return Flux.from(body).take(0, false).doOnDiscard(DataBuffer.class, DataBufferUtils::release).then();
    }

    private void disposeOrigin() {
        Connection connection = exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR);
        if (connection != null) {
            connection.dispose();
        }
    }

    private static ResponseCache.UpstreamFailure classify(Throwable error) {
        if (error instanceof TimeoutException || error instanceof io.netty.handler.timeout.TimeoutException
                || error instanceof ResponseStatusException status && status.getStatusCode().value() == 504) {
            return ResponseCache.UpstreamFailure.TIMEOUT;
        }
        if (error instanceof IOException || error instanceof reactor.netty.http.client.PrematureCloseException) {
            return ResponseCache.UpstreamFailure.CONNECTION;
        }
        return null;
    }

    private void applyTimeoutMetadata() {
        if (policy.timeout() == null) {
            return;
        }
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return;
        }
        HashMap<String, Object> metadata = new HashMap<>(route.getMetadata());
        metadata.put(RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, Math.toIntExact(policy.timeout().connect().toMillis()));
        metadata.put(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, policy.timeout().read().toMillis());
        Route configured = Route.async().id(route.getId()).uri(route.getUri()).order(route.getOrder())
                .asyncPredicate(route.getPredicate()).filters(route.getFilters()).metadata(metadata).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, configured);
    }
}
