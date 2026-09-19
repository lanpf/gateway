package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.policy.execution.CapabilityOutcome;
import com.cloud.gateway.domain.policy.execution.CapabilityUnit;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** A typed batch executes all its units and aggregates its own outcome. */
public interface CapabilityExecutor<U extends CapabilityUnit> {
    String capability();
    Class<U> unitType();
    CompletionStage<? extends CapabilityOutcome> execute(List<U> units);

    default CompletionStage<? extends CapabilityOutcome> executeUnits(List<CapabilityUnit> units) {
        return execute(units.stream().map(unitType()::cast).toList());
    }
}
