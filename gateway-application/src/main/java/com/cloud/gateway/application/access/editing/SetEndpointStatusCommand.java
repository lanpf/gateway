package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointStatus;

/** Dedicated enable/disable transition of one endpoint; the only command that changes its status. */
public record SetEndpointStatusCommand(String expectedSourceRevision, EndpointId endpointId, EndpointStatus status) { }
