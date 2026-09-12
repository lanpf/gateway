package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import java.util.List;

/** Path-grammar adapter returns matching facts, without choosing a winner. */
public interface EndpointMatchSource {
    List<Endpoint> match(EndpointRegistry registry, String method, String path);
}
