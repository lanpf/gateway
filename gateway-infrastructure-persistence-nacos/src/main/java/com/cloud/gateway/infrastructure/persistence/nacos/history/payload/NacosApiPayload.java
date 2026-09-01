package com.cloud.gateway.infrastructure.persistence.nacos.history.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosApiPayload<T>(
        @JsonProperty("code") Integer code,
        @JsonProperty("message") String message,
        @JsonProperty("data") T data
) { }
