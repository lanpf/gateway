package com.cloud.gateway.infrastructure.governance.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Node-local resource limits, not per-request policy or snapshot content. */
@Getter
@Setter
@Validated
@ConfigurationProperties("gateway.governance")
public class GovernanceProperties {
    @Min(1024)
    private long cacheMaximumWeightBytes = 64L * 1024 * 1024;

    @Min(1)
    private long maximumCircuits = 10000;
}
