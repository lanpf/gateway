package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.runtime.TimeoutCapability;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Hands the declared budget forward. Nothing is enforced here: the unit is a declaration and the
 * transport applies it, so execution only makes the value visible to the adapter as an outcome.
 */
public final class TimeoutCapabilityExecutor implements CapabilityExecutor<TimeoutCapability.Unit> {
    @Override
    public CapabilityId capability() { return TimeoutCapability.ID; }

    @Override
    public Class<TimeoutCapability.Unit> unitType() { return TimeoutCapability.Unit.class; }

    @Override
    public CompletionStage<TimeoutCapability.Outcome> execute(List<TimeoutCapability.Unit> units) {
        return CompletableFuture.completedFuture(new TimeoutCapability.Outcome(units.get(0).policy()));
    }
}
