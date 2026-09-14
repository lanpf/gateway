package com.cloud.gateway.domain.policy.capability;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Set;

/**
 * Execution record of one quota slot: the rules whose quota was consumed, and the slot outcome.
 * Later slots consult it so one rule executes at most once per request across stages.
 */
public record QuotaExecution(Set<RateLimitRuleId> executedRuleIds, RateLimitDecision outcome) {
    public QuotaExecution {
        executedRuleIds = executedRuleIds == null ? Set.of() : Set.copyOf(executedRuleIds);
        Require.notNull(outcome, DomainException::missingField);
    }

    /** Cross-slot dedup: has this rule already consumed quota in an earlier slot? */
    public boolean executed(RateLimitRuleId ruleId) {
        return executedRuleIds.contains(ruleId);
    }
}
