package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.quota.RequestQuotaExecutor;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.policy.bundle.EffectivePolicy;
import com.cloud.gateway.domain.policy.bundle.PolicyResolver;
import com.cloud.gateway.domain.policy.capability.RateLimitDimension;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;

/** Captures one publication and applies pre-authentication protection, including unknown endpoints. */
@RequiredArgsConstructor
public final class IngressPreparationCommandService {
    private final PolicySnapshotProvider provider;
    private final EndpointMatchSource matches;
    private final RequestQuotaExecutor quotas;
    private final PolicyResolver policies = new PolicyResolver();

    public CompletionStage<IngressPreparationOutput> prepare(IngressPreparationCommand command) {
        IngressAccessSnapshot snapshot = provider.current().snapshot();
        List<Endpoint> candidates = matches.match(
                snapshot.content().endpointRegistry(), command.method(), command.path());
        Endpoint endpoint = snapshot.content().endpointRegistry().unambiguousCandidate(candidates).orElse(null);
        EffectivePolicy policy = policies.resolve(snapshot.content().policies(), endpoint, null, null, null);
        Set<String> applied = new HashSet<>();
        IngressTask<IngressPreparationOutput> task = new IngressTask<>();
        task.finish(task.track(quotas.acquire(policy.rateLimits(),
                Map.of(RateLimitDimension.IP, command.remoteIp()), true, applied))
                .thenApply(decision -> {
                    if (decision.allowed()) {
                        snapshot.content().endpointRegistry().select(candidates);
                    }
                    return new IngressPreparationOutput(snapshot, endpoint, decision, applied);
                }));
        return task;
    }

    /** Transport matching supplies candidates; uniqueness belongs to the registry. */
    public Optional<Endpoint> select(Collection<Endpoint> candidates) {
        Require.notNull(candidates, DomainException::missingField);
        if (candidates.size() > 1) {
            throw new DomainException(DomainError.ENDPOINT_AMBIGUOUS);
        }
        return unambiguousCandidate(candidates);
    }

    /** Only a unique candidate may contribute endpoint-specific pre-authentication protection. */
    public Optional<Endpoint> unambiguousCandidate(Collection<Endpoint> candidates) {
        Require.notNull(candidates, DomainException::missingField);
        candidates.forEach(candidate -> Require.notNull(candidate, DomainException::missingField));
        return candidates.size() == 1 ? candidates.stream().findFirst() : Optional.empty();
    }
}
