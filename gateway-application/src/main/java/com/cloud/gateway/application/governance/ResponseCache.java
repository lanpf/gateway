package com.cloud.gateway.application.governance;

import com.cloud.gateway.domain.policy.capability.CachePolicy;
import com.cloud.gateway.domain.policy.capability.CircuitBreakerPolicy;
import com.cloud.gateway.domain.policy.capability.CacheRequestFacts;
import java.util.Optional;
import java.time.Duration;

/** Local, bounded representation cache. Every lookup rechecks current request eligibility. */
public interface ResponseCache {
    /** Parse original protocol evidence without deciding gateway cache policy. */
    CacheRequestFacts requestFacts(CacheRequest request);

    Optional<CachedRepresentation> lookup(CacheRequest request, CachePolicy policy);

    default boolean store(CacheRequest request, CachePolicy policy, CachedRepresentation representation) {
        return store(request, policy, representation, Duration.ZERO);
    }

    /** Buffered transport adapters pass elapsed upstream/collection time so an existing Age value
     * cannot be refreshed while waiting for the body. The three-argument form assumes immediate receipt.
     */
    boolean store(CacheRequest request, CachePolicy policy, CachedRepresentation representation, Duration responseDelay);

    /** Only call after a classified upstream failure and before committing the downstream response.
     * Authentication, authorization, rate rejection and arbitrary local exceptions are not eligible.
     */
    Optional<CachedRepresentation> fallback(CacheRequest request, CachePolicy policy,
            CircuitBreakerPolicy.Fallback fallback, UpstreamFailure failure);

    enum UpstreamFailure { CONNECTION, TIMEOUT, SERVER_ERROR, CIRCUIT_OPEN }
}
