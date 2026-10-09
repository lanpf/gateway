package com.cloud.gateway.application.quota;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitConfig;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record QuotaCommand(List<RateLimitConfig> groups, Map<RateLimitDimension, String> dimensions, boolean ipOnly) {
    public QuotaCommand {
        groups = NullSafe.copyOf(groups);
        EnumMap<RateLimitDimension, String> copy = new EnumMap<>(RateLimitDimension.class);
        Require.notNull(dimensions);
        NullSafe.forEach(dimensions.keySet());
        dimensions.forEach((dimension, value) -> {
            if (org.apache.commons.lang3.StringUtils.isNotBlank(value)) { copy.put(dimension, value); }
        });
        // Domain applicability owns the uniform treatment of absent/blank dimensions.
        dimensions = Collections.unmodifiableMap(copy);
    }
}
