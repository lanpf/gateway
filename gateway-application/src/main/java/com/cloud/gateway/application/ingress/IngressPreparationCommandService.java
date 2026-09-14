package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.access.publication.AccessSnapshotProvider;
import com.cloud.gateway.application.quota.RequestQuotaExecutor;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.access.RequestEvidence;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.policy.bundle.PolicyResolver;
import com.cloud.gateway.domain.policy.capability.QuotaExecution;
import com.cloud.gateway.domain.policy.capability.RateLimitDimension;
import com.cloud.gateway.domain.policy.capability.RateLimitRuleId;
import com.cloud.gateway.domain.policy.capability.RateLimitRules;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;

/**
 * Stage one of ingress admission: capture the snapshot once, classify matched candidates and
 * execute the pre-authentication IP quota slot. Ambiguity and unknown paths are not rejected here.
 */
@RequiredArgsConstructor
public final class IngressPreparationCommandService {
    private final com.cloud.gateway.application.access.publication.AccessSnapshotProvider provider;
    private final EndpointMatchSource matches;
    private final RequestQuotaExecutor quotas;
    private final PreparationObserver observer;
    private final PolicyResolver policies = new PolicyResolver();

    public CompletionStage<IngressPreparationOutput> prepare(PrepareIngressCommand command) {
        RequestEvidence evidence = command.evidence();
        IngressAccessSnapshot snapshot = provider.current().snapshot();
        List<Endpoint> candidates = matches.match(
                snapshot.content().endpointRegistry(), evidence.method(), evidence.rawPath());
        EndpointMatch match = EndpointMatch.of(candidates);
        if (match instanceof EndpointMatch.Ambiguous) {
            observer.ambiguousMatch();
        }
        Endpoint endpoint = match instanceof EndpointMatch.Unique unique ? unique.endpoint() : null;

        List<RateLimitRules> groups = policies.preAuthRules(snapshot.content().policies(), endpoint);

        Set<RateLimitRuleId> executed = new HashSet<>();
        IngressTask<IngressPreparationOutput> task = new IngressTask<>();
        task.finish(task.track(quotas.acquire(groups,
                        Map.of(RateLimitDimension.IP, evidence.remoteIp()), true, executed))
                .thenApply(decision -> {
                    QuotaExecution execution = new QuotaExecution(executed, decision);
                    return decision.allowed()
                            ? new IngressPreparationOutput.Prepared(snapshot, match, evidence, execution)
                            : new IngressPreparationOutput.Denied(decision);
                }));
        return task;
    }
}
