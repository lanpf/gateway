package com.cloud.gateway.application.governance;

import com.cloud.gateway.domain.policy.bundle.EffectivePolicy;
import com.cloud.gateway.domain.policy.capability.DownstreamGovernanceRules;
import com.cloud.gateway.domain.policy.capability.CacheRequestFacts;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Coordinates existing cache and circuit ports without depending on the HTTP execution mechanism. */
@RequiredArgsConstructor
public final class DownstreamGovernanceCoordinator {
    @NonNull
    private final ResponseCache cache;
    @NonNull
    private final DownstreamCircuitBreaker circuits;
    private final DownstreamGovernanceRules rules = new DownstreamGovernanceRules();

    public DownstreamAdmissionOutput prepare(CacheRequest request, EffectivePolicy policy) {
        CacheRequestFacts facts = cache.requestFacts(request);
        Optional<CachedRepresentation> cached = rules.canReadCache(policy.cache(), facts)
                ? cache.lookup(request, policy.cache()) : Optional.empty();
        if (cached.isPresent()) {
            return new DownstreamAdmissionOutput.CacheHit(cached.orElseThrow());
        }
        DownstreamCircuitBreaker.Permit permit = policy.circuitBreaker() == null ? null
                : circuits.acquire(request.routeId(), policy.circuitBreaker());
        DownstreamExecution execution = new DownstreamExecution(cache, request, facts, policy, permit);
        return execution.allowed() ? new DownstreamAdmissionOutput.Execute(execution)
                : new DownstreamAdmissionOutput.Fallback(execution, ResponseCache.UpstreamFailure.CIRCUIT_OPEN);
    }
}
