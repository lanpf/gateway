package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.capability.ratelimit.ApplicableQuota;
import java.util.List;
import java.util.Objects;

public record PlannedQuotaCommand(List<ApplicableQuota> quotas) {
    public PlannedQuotaCommand {
        quotas = List.copyOf(Objects.requireNonNull(quotas, "quotas"));
    }
}
