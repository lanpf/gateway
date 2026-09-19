package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import com.cloud.gateway.domain.policy.capability.ApplicableQuota;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** Non-blocking execution of an already planned quota batch. */
@FunctionalInterface
public interface RequestQuotaExecutor {
    CompletionStage<RateLimitDecision> acquire(List<ApplicableQuota> quotas);
}
