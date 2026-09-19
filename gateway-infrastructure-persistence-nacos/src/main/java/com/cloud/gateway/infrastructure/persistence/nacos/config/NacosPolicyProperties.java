package com.cloud.gateway.infrastructure.persistence.nacos.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(NacosPolicyProperties.PREFIX)
public class NacosPolicyProperties {
    public static final String PREFIX = "gateway.access.nacos";
    public static final String DEFAULT_GROUP = "GATEWAY";
    public static final String DEFAULT_DATA_ID = "gateway-access.yaml";

    @NotBlank private String group = DEFAULT_GROUP;
    @NotBlank private String dataId = DEFAULT_DATA_ID;
    @NotBlank private String endpointDefinitionDataId = "gateway-endpoints.yaml";
    @NotBlank private String appDefinitionDataId = "gateway-app-capabilityComposition.yaml";
    @NotBlank private String clientDefinitionDataId = "gateway-client-capabilityComposition.yaml";
    @NotBlank private String policyConfigurationDefinitionDataId = "gateway-access-configuration.yaml";
    @NotNull @DurationMin(seconds = 1) private Duration readTimeout = Duration.ofSeconds(5);
    @NotNull @DurationMin(seconds = 1) private Duration recoveryInterval = Duration.ofSeconds(30);
    @NotNull private Path lastKnownGoodLocation = Path.of("data", DEFAULT_DATA_ID);
}
