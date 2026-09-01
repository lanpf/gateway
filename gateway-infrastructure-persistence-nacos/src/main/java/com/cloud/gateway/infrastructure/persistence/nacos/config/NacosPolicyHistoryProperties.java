package com.cloud.gateway.infrastructure.persistence.nacos.config;

import com.cloud.framework.core.http.RestClientProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(NacosPolicyHistoryProperties.PREFIX)
public class NacosPolicyHistoryProperties {
    public static final String PREFIX = NacosPolicyProperties.PREFIX + ".history";

    @Min(1) @Max(200) private int pageSize = 100;
    @Min(1) private int maxPages = 20;
    private String adminAccessToken;
    @Valid private final RestClientProperties restClient = new RestClientProperties();
}
