package com.cloud.gateway.interfaces.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("gateway.security")
public class GatewaySecurityEntryProperties {
    @NotBlank private String adminAudience;
}
