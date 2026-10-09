package com.cloud.gateway.domain.capability.runtime;

import com.cloud.framework.core.error.FrameworkException;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.CapabilityId;

import java.util.List;

public record CapabilityPlan(List<Step> steps) {
    public CapabilityPlan {
        steps = NullSafe.copyOf(steps);
    }

    public record Step(CapabilityId capability, List<CapabilityUnit> units, List<CapabilityExecutionKey> keys) {
        public Step {
            Require.notNull(capability);
            units = NullSafe.copyOf(Require.notEmpty(units));
            keys = NullSafe.copyOf(Require.notEmpty(keys));
            if (units.size() != keys.size()) {
                throw invalid();
            }
            for (int index = 0; index < units.size(); index++) {
                if (!capability.equals(keys.get(index).capability())
                        || !units.get(index).unitId().equals(keys.get(index).unitId())) {
                    throw invalid();
                }
            }
        }
    }

    private static FrameworkException invalid() {
        return FrameworkException.invalidObject();
    }
}
