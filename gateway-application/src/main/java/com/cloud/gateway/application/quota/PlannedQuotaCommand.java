package com.cloud.gateway.application.quota;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.ratelimit.ApplicableQuota;
import java.util.List;
import java.util.Objects;

public record PlannedQuotaCommand(List<ApplicableQuota> quotas) {
    public PlannedQuotaCommand {
        quotas = NullSafe.copyOf(Require.notNull(quotas));
    }
}
