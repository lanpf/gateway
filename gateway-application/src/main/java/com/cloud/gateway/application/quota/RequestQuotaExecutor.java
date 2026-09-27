package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.ApplicableQuota;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** Non-blocking execution of an already planned quota batch. */
@FunctionalInterface
public interface RequestQuotaExecutor {
    CompletionStage<RateLimitDecision> acquire(List<ApplicableQuota> quotas);
}
