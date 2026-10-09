package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment-owned admission rules, captured once when the application is assembled.
 * deny-set / deny-remove: names forbidden for the respective operation.
 */
@ConfigurationProperties(prefix = "gateway.header")
public record HeaderAdmissionProperties(Set<String> denySet, Set<String> denyRemove) {
    public HeaderAdmissionProperties {
        denySet = denySet == null ? Set.of() : NullSafe.copyOf(denySet);
        denyRemove = denyRemove == null ? Set.of() : NullSafe.copyOf(denyRemove);
    }

    public HeaderConfigRule toRules() {
        return new HeaderConfigRule(denySet, denyRemove);
    }
}
