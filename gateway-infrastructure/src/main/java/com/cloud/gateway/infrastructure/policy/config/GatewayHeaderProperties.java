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
    private final Set<String> extraHeaders = new LinkedHashSet<>();
    private final Set<String> forbiddenSetHeaders = new LinkedHashSet<>();
    private final Set<String> forbiddenRemoveHeaders = new LinkedHashSet<>();

    public HeaderRules toRules() {
        return new HeaderRules(extraHeaders, forbiddenSetHeaders, forbiddenRemoveHeaders);
    }
}
