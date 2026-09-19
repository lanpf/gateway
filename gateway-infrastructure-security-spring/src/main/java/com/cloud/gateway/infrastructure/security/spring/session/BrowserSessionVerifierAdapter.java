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

    @Override
    public CompletionStage<BrowserSessionVerificationOutcome> verify(String sessionToken) {
        return webClient.post()
                .uri(UserAuthPathApiConstants.INTERNAL_BROWSER_SESSIONS_VERIFY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new VerifyBrowserSessionApiCommand(sessionToken))
                .retrieve()
                .bodyToMono(VERIFY_RESPONSE_TYPE)
                .map(payload -> subject(payload, clock.instant()))
                .switchIfEmpty(Mono.fromSupplier(() -> unavailableInvalidResponse("empty response")))
                .onErrorResume(WebClientResponseException.class, exception ->
                        Mono.just(responseFailure(exception)))
                .onErrorResume(WebClientRequestException.class, exception -> {
                    log.warn("Browser Session verification dependency is unavailable: {}",
                            exception.getClass().getSimpleName());
                    return Mono.just(BrowserSessionVerificationOutcome.unavailable(
                            SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE));
                })
                .onErrorResume(RuntimeException.class, exception ->
                        Mono.just(unavailableInvalidResponse(exception.getClass().getSimpleName())))
                .toFuture();
    }

    static BrowserSessionVerificationOutcome subject(
            Result<VerifyBrowserSessionApiCommandOutput> payload, Instant now) {
        if (payload == null) {
            return unavailableInvalidResponse("missing response");
        }
        if (!payload.isSuccess()) {
            return unavailableInvalidResponse("unsuccessful response code " + payload.code());
        }
        if (payload.data() == null) {
            return unavailableInvalidResponse("missing payload data");
        }
        VerifyBrowserSessionApiCommandOutput data = payload.data();
        if (!data.verified()) {
            return BrowserSessionVerificationOutcome.invalid();
        }
        if (data.userId() == null || data.userId() <= 0
                || !StringUtils.hasText(data.sessionId())
                || data.remainingIdleTtlSeconds() == null
                || data.remainingIdleTtlSeconds() <= 0) {
            return unavailableInvalidResponse("invalid verified session fields");
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

    private static BrowserSessionVerificationOutcome responseFailure(WebClientResponseException exception) {
        if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 404) {
            return BrowserSessionVerificationOutcome.invalid();
        }
        if (exception.getStatusCode().is5xxServerError()) {
            log.warn("Browser Session verification dependency returned status={}",
                    exception.getStatusCode().value());
            return BrowserSessionVerificationOutcome.unavailable(
                    SubjectAuthenticationFailReason.UPSTREAM_UNAVAILABLE);
        }
        return unavailableInvalidResponse("unexpected HTTP status " + exception.getStatusCode().value());
    }

    private static BrowserSessionVerificationOutcome unavailableInvalidResponse(String reason) {
        log.warn("Browser Session verification returned an invalid response: {}", reason);
        return BrowserSessionVerificationOutcome.unavailable(
                SubjectAuthenticationFailReason.RESPONSE_INVALID);
    }
}
