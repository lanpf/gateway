package com.cloud.gateway.infrastructure.persistence.nacos.history.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosHistoryDetailPayload(
        @JsonProperty("id") String id,
        @JsonProperty("content") String content,
        @JsonProperty("md5") String md5,
        @JsonProperty("opType") String operationType
) { }
