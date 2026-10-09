package com.cloud.gateway.infrastructure.security.spring.session;

import com.cloud.framework.core.Result;
import com.cloud.framework.core.SubjectType;
import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.application.security.BrowserSessionVerificationOutcome;
import com.cloud.gateway.domain.ingress.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.ingress.access.subject.SessionId;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationFailReason;
import com.cloud.gateway.domain.ingress.access.subject.SubjectId;
import com.cloud.userauth.api.authentication.VerifyBrowserSessionApiCommand;
import com.cloud.userauth.api.authentication.VerifyBrowserSessionApiCommandOutput;
import com.cloud.userauth.api.constants.UserAuthPathApiConstants;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * 通过 user-auth 浏览器会话在线验证端点验证 BROWSER_SESSION Cookie 的适配器；
 * 会话无效与验证端点不可达使用不同 Outcome 表达，并始终保持 fail-closed。
 */
@Slf4j
@RequiredArgsConstructor
public final class BrowserSessionVerifierAdapter implements BrowserSessionVerifier {
    private static final ParameterizedTypeReference<Result<VerifyBrowserSessionApiCommandOutput>>
            VERIFY_RESPONSE_TYPE = new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final Clock clock;
    /** user-auth 依赖故障态门控：进入故障态 WARN 一次、恢复时 INFO 一次，避免逐请求重复记录。 */
    private final AtomicBoolean degraded = new AtomicBoolean();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong lastSuccessMillis = new AtomicLong();

    @Override
    public CompletionStage<BrowserSessionVerificationOutcome> verify(String sessionToken) {
        return webClient.post()
                .uri(UserAuthPathApiConstants.INTERNAL_BROWSER_SESSIONS_VERIFY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new VerifyBrowserSessionApiCommand(sessionToken))
                .retrieve()
                .bodyToMono(VERIFY_RESPONSE_TYPE)
                .map(payload -> subject(payload, clock.instant()))
                .switchIfEmpty(Mono.fromSupplier(BrowserSessionVerifierAdapter::invalidResponse))
                .onErrorResume(WebClientResponseException.class, exception ->
                        Mono.just(responseFailure(exception)))
                .onErrorResume(WebClientRequestException.class, exception -> {
                    reportDegraded(() -> log.warn("Browser Session verification dependency is unavailable: {}",
                            exception.getClass().getSimpleName()));
                    return Mono.just(BrowserSessionVerificationOutcome.unavailable(
                            SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE));
                })
                .onErrorResume(RuntimeException.class, exception ->
                        Mono.just(invalidResponse()))
                .map(outcome -> {
                    if (outcome instanceof BrowserSessionVerificationOutcome.Unavailable unavailable) {
                        failures.incrementAndGet();
                        reportDegraded(() -> log.warn("Browser Session verification dependency degraded reason={}", unavailable.reason()));
                    }
                    if (outcome instanceof BrowserSessionVerificationOutcome.Verified
                            || outcome instanceof BrowserSessionVerificationOutcome.Invalid) {
                        reportRecovered();
                    }
                    return outcome;
                })
                .toFuture();
    }

    public void bindMetrics(MeterRegistry registry) {
        com.cloud.framework.core.validation.Require.notNull(registry);
        FunctionCounter.builder("gateway.browser_session.verification.failures", failures, AtomicLong::doubleValue).register(registry);
        Gauge.builder("gateway.browser_session.verification.last_success_seconds", lastSuccessMillis,
                value -> value.get() / 1000.0).register(registry);
        Gauge.builder("gateway.browser_session.verification.degraded", degraded, value -> value.get() ? 1 : 0).register(registry);
    }

    private void reportDegraded(Runnable warning) {
        if (degraded.compareAndSet(false, true)) {
            // user-auth 异常文本可能携带会话 key；只记录异常类型/状态码，不落原文（参照 RedisQuotaAcquirer）。
            warning.run();
        }
    }

    private void reportRecovered() {
        lastSuccessMillis.set(clock.millis());
        if (degraded.compareAndSet(true, false)) {
            log.info("Browser Session verification dependency recovered");
        }
    }

    static BrowserSessionVerificationOutcome subject(
            Result<VerifyBrowserSessionApiCommandOutput> payload, Instant now) {
        if (payload == null) {
            return invalidResponse();
        }
        if (!payload.isSuccess()) {
            return invalidResponse();
        }
        if (payload.data() == null) {
            return invalidResponse();
        }
        VerifyBrowserSessionApiCommandOutput data = payload.data();
        if (!data.verified()) {
            return BrowserSessionVerificationOutcome.invalid();
        }
        if (data.userId() == null || data.userId() <= 0
                || !StringUtils.hasText(data.sessionId())
                || data.remainingIdleTtlSeconds() == null
                || data.remainingIdleTtlSeconds() <= 0) {
            return invalidResponse();
        }
        Duration remainingIdleTtl = Duration.ofSeconds(data.remainingIdleTtlSeconds());
        return BrowserSessionVerificationOutcome.verified(new AuthenticatedSubject(
                SubjectType.BROWSER_SESSION,
                new SubjectId(String.valueOf(data.userId())),
                new SessionId(data.sessionId()),
                null,
                Set.of(),
                Set.of(),
                now.plus(remainingIdleTtl)));
    }

    private BrowserSessionVerificationOutcome responseFailure(WebClientResponseException exception) {
        if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 404) {
            return BrowserSessionVerificationOutcome.invalid();
        }
        if (exception.getStatusCode().is5xxServerError()) {
            reportDegraded(() -> log.warn("Browser Session verification dependency returned status={}",
                    exception.getStatusCode().value()));
            return BrowserSessionVerificationOutcome.unavailable(
                    SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE);
        }
        return invalidResponse();
    }

    private static BrowserSessionVerificationOutcome invalidResponse() {
        return BrowserSessionVerificationOutcome.unavailable(
                SubjectAuthenticationFailReason.RESPONSE_INVALID);
    }
}
