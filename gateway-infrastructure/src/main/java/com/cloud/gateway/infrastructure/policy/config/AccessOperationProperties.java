package com.cloud.gateway.infrastructure.policy.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("gateway.access.operations")
public class AccessOperationProperties {
    @NotNull @DurationMin(seconds = 1)
    private Duration timeout = Duration.ofSeconds(60);
    @NotNull @DurationMin(seconds = 1)
    private Duration maxBlockingTime = Duration.ofSeconds(10);
    @NotNull @DurationMin(seconds = 1)
    private Duration leaseSafetyMargin = Duration.ofSeconds(20);
}
