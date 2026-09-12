package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.policy.header.HeaderRules;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-owned admission rules, captured once when the application is assembled. */
@Getter
@ConfigurationProperties(prefix = "gateway.headers")
public final class GatewayHeaderProperties {
    private final Set<String> allowedHeaders = new LinkedHashSet<>();
    private final Set<String> unsettableHeaders = new LinkedHashSet<>();
    private final Set<String> unremovableHeaders = new LinkedHashSet<>();

    public HeaderRules toRules() {
        return new HeaderRules(allowedHeaders, unsettableHeaders, unremovableHeaders);
    }
}
