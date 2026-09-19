package com.cloud.gateway.domain.policy.execution;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.util.List;

public record CapabilityPlan(List<Step> steps) {
    public CapabilityPlan {
        steps = List.copyOf(Require.notNull(steps, DomainException::missingField));
    }

    public record Step(String capability, List<CapabilityUnit> units, List<CapabilityExecutionKey> keys) {
        public Step {
            Require.notBlank(capability, DomainException::missingField);
            units = List.copyOf(Require.notEmpty(units, DomainException::missingField));
            keys = List.copyOf(Require.notEmpty(keys, DomainException::missingField));
            if (units.size() != keys.size()) {
                throw DomainException.invalidField();
            }
            for (int index = 0; index < units.size(); index++) {
                if (!capability.equals(keys.get(index).capability())
                        || !units.get(index).unitId().equals(keys.get(index).unitId())) {
                    throw DomainException.invalidField();
                }
            }
        }
    }
}
