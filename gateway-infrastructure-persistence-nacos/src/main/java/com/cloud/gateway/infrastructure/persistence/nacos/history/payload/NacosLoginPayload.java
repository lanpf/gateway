package com.cloud.gateway.infrastructure.persistence.nacos.history.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosLoginPayload(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("tokenTtl") Long tokenTtl
) { }
