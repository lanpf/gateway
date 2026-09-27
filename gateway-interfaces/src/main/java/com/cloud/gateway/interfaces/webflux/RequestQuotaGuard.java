package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.quota.PlannedQuotaCommand;
import com.cloud.gateway.domain.capability.ratelimit.ApplicableQuota;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.application.quota.RequestQuotaExecutor;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Blocking-capable asynchronous bridge and HTTP projection, without quota rules. */
@RequiredArgsConstructor
public final class RequestQuotaGuard implements RequestQuotaExecutor {
    private final QuotaCommandService quotaCommandService;

    @Override
    public CompletionStage<RateLimitDecision> acquire(List<ApplicableQuota> quotas) {
        PlannedQuotaCommand command = new PlannedQuotaCommand(quotas);
        return Mono.fromCallable(() -> quotaCommandService.acquire(command))
                .subscribeOn(Schedulers.boundedElastic()).toFuture();
    }

    public Mono<Boolean> respond(ServerWebExchange exchange, RateLimitDecision decision) {
        return Mono.defer(() -> {
            if (decision.allowed()) {
                return Mono.just(true);
            }
            exchange.getResponse().setStatusCode(decision.unavailable()
                    ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.TOO_MANY_REQUESTS);
            if (!decision.unavailable()) {
                long seconds = decision.retryAfter().getSeconds();
                if (decision.retryAfter().getNano() > 0 && seconds < Long.MAX_VALUE) {
                    seconds++;
                }
                exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER,
                        Long.toString(Math.max(1, seconds)));
            }
            return exchange.getResponse().setComplete().thenReturn(false);
        });
    }

}
