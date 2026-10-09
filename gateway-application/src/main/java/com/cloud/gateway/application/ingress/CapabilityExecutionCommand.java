package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.capability.runtime.CapabilityContext;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecutionKey;
import java.util.Objects;
import java.util.Set;

/** One slot's facts from the captured snapshot, plus the request's prior execution keys. */
public record CapabilityExecutionCommand(CapabilityPolicyConfig policyConfig, CapabilityContext context,
                                         Set<CapabilityExecutionKey> executed) {
    public CapabilityExecutionCommand {
        Require.allNotNull(policyConfig, context);
        executed = NullSafe.copyOf(Require.notNull(executed));
    }
}
