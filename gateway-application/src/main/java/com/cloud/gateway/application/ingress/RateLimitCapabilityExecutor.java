package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.quota.RequestQuotaExecutor;
import com.cloud.gateway.domain.policy.capability.RateLimitCapability;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class RateLimitCapabilityExecutor implements CapabilityExecutor<RateLimitCapability.Unit> {
    private final RequestQuotaExecutor quotas;

    @Override
    public String capability() { return RateLimitCapability.ID; }

    @Override
    public Class<RateLimitCapability.Unit> unitType() { return RateLimitCapability.Unit.class; }

    @Override
    public CompletionStage<RateLimitCapability.Outcome> execute(List<RateLimitCapability.Unit> units) {
        IngressTask<RateLimitCapability.Outcome> task = new IngressTask<>();
        task.finish(task.track(quotas.acquire(units.stream().map(RateLimitCapability.Unit::quota).toList()))
                .thenApply(RateLimitCapability.Outcome::new));
        return task;
    }
}
