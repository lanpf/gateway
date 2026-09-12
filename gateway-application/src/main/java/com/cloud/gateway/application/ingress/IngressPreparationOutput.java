package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import java.util.Set;

public record IngressPreparationOutput(IngressAccessSnapshot snapshot, Endpoint endpoint,
                                       RateLimitDecision quota, Set<String> appliedRules) {
    public IngressPreparationOutput {
        appliedRules = Set.copyOf(appliedRules);
    }
}
