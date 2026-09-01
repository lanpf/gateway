package com.cloud.gateway.infrastructure.persistence.nacos.history.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosHistoryPagePayload(
        @JsonProperty("pageItems") List<NacosHistoryItemPayload> pageItems
) { }
