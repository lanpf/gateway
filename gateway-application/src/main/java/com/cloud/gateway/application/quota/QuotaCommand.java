package com.cloud.gateway.application.quota;

import com.cloud.gateway.domain.policy.capability.RateLimitDimension;
import com.cloud.gateway.domain.policy.capability.RateLimitRules;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record QuotaCommand(List<RateLimitRules> groups, Map<RateLimitDimension, String> dimensions, boolean ipOnly) {
    public QuotaCommand {
        groups = List.copyOf(groups);
        EnumMap<RateLimitDimension, String> copy = new EnumMap<>(RateLimitDimension.class);
        copy.putAll(Objects.requireNonNull(dimensions, "dimensions must not be null"));
        // Domain applicability owns the uniform treatment of absent/blank dimensions.
        dimensions = Collections.unmodifiableMap(copy);
    }
}
