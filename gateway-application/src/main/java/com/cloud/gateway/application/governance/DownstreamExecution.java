package com.cloud.gateway.application.governance;

import com.cloud.gateway.domain.policy.bundle.EffectivePolicy;
import com.cloud.gateway.domain.policy.capability.DownstreamGovernanceRules;
import com.cloud.gateway.domain.policy.capability.CacheRequestFacts;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Per-attempt coordination. Header observation ends circuit timing; termination releases unused admission. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class DownstreamExecution {
    private final ResponseCache cache;
    private final CacheRequest request;
    private final CacheRequestFacts facts;
    private final EffectivePolicy policy;
    private final DownstreamCircuitBreaker.Permit permit;
    private final DownstreamGovernanceRules rules = new DownstreamGovernanceRules();
    private final AtomicBoolean headersSeen = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();

    public boolean allowed() {
        return permit == null || permit.allowed();
    }

    public void observeHeaders(int status) {
        if (headersSeen.compareAndSet(false, true)) {
            complete(switch (rules.observe(status)) {
                case SUCCESS -> DownstreamCircuitBreaker.Outcome.SUCCESS;
                case UPSTREAM_FAILURE -> DownstreamCircuitBreaker.Outcome.UPSTREAM_FAILURE;
                case IGNORED -> DownstreamCircuitBreaker.Outcome.IGNORED;
            });
        }
    }

    /** A null classification means a local/unclassified error or an already committed response. */
    public Optional<ResponseCache.UpstreamFailure> fail(ResponseCache.UpstreamFailure classification) {
        ResponseCache.UpstreamFailure failure = headersSeen.get() ? null : classification;
        complete(failure == null ? DownstreamCircuitBreaker.Outcome.IGNORED
                : DownstreamCircuitBreaker.Outcome.UPSTREAM_FAILURE);
        return Optional.ofNullable(failure);
    }

    public void finish() {
        complete(DownstreamCircuitBreaker.Outcome.IGNORED);
    }

    private void complete(DownstreamCircuitBreaker.Outcome outcome) {
        if (permit != null && completed.compareAndSet(false, true)) {
            permit.complete(outcome);
        }
    }

    public boolean canFallback(int status) {
        return rules.isOriginFailure(status) && stale(ResponseCache.UpstreamFailure.SERVER_ERROR).isPresent();
    }

    /** Never retain a stale candidate: each call asks the cache to recheck absolute expiry. */
    public Optional<CachedRepresentation> stale(ResponseCache.UpstreamFailure failure) {
        if (!rules.canReadCache(policy.cache(), facts) || policy.circuitBreaker() == null
                || !rules.canFallback(policy.circuitBreaker().fallback(), failure != null)) {
            return Optional.empty();
        }
        return cache.fallback(request, policy.cache(), policy.circuitBreaker().fallback(), failure);
    }

    public boolean canCapture(int status) {
        return rules.canCapture(policy.cache(), facts, status);
    }

    /** Final eligibility and freshness remain the cache port's responsibility. */
    public void store(CachedRepresentation representation, Duration responseDelay) {
        cache.store(request, policy.cache(), representation, responseDelay);
    }
}
