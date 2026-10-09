package com.cloud.gateway.infrastructure.persistence.nacos.history.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NacosHistoryPageResponsePayload(
        @JsonProperty("pageItems") List<NacosHistoryItemResponsePayload> pageItems
) { }
