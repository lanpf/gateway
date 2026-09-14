package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.RateLimitDimension;
import com.cloud.gateway.domain.policy.capability.RateLimitRuleId;
import com.cloud.gateway.domain.policy.capability.RateLimitRules;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Non-blocking boundary. Share applied within one request and invoke phases serially. */
@FunctionalInterface
public interface RequestQuotaExecutor {
    CompletionStage<RateLimitDecision> acquire(List<RateLimitRules> groups,
            Map<RateLimitDimension, String> dimensions, boolean ipOnly, Set<RateLimitRuleId> applied);
}
