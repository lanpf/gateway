package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.runtime.HeaderApplyCapability;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Hands the settled header plan forward. Nothing is applied here: mutating the upstream request needs
 * the transport's request object, which this layer deliberately cannot see, so execution only makes
 * the plan visible to the adapter as an outcome.
 */
public final class HeaderApplyCapabilityExecutor
        implements CapabilityExecutor<HeaderApplyCapability.Unit> {

    @Override
    public CapabilityId capability() { return HeaderApplyCapability.ID; }

    @Override
    public Class<HeaderApplyCapability.Unit> unitType() { return HeaderApplyCapability.Unit.class; }

    @Override
    public CompletionStage<HeaderApplyCapability.Outcome> execute(List<HeaderApplyCapability.Unit> units) {
        return CompletableFuture.completedFuture(new HeaderApplyCapability.Outcome(units.get(0).policy()));
    }
}
