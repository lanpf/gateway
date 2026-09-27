package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment-owned admission rules, captured once when the application is assembled.
 * extra: header names admitted for ordinary set/remove beyond registered RequestHeader and the gateway prefix;
 * deny-set / deny-remove: names forbidden for the respective operation.
 */
@ConfigurationProperties(prefix = "gateway.header")
public record HeaderAdmissionProperties(Set<String> extra, Set<String> denySet, Set<String> denyRemove) {
    public HeaderAdmissionProperties {
        extra = extra == null ? Set.of() : Set.copyOf(extra);
        denySet = denySet == null ? Set.of() : Set.copyOf(denySet);
        denyRemove = denyRemove == null ? Set.of() : Set.copyOf(denyRemove);
    }

    public HeaderAdmissionRules toRules() {
        return new HeaderAdmissionRules(extra, denySet, denyRemove);
    }
}
