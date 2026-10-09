package com.cloud.gateway.domain.capability.header;

import com.cloud.framework.core.validation.Require;

/** One selected Header configuration, including the safe default when no binding declares one. */
public record EffectiveHeaderPolicy(HeaderConfig config) {
    public EffectiveHeaderPolicy {
        Require.notNull(config);
    }
}
