package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointStatus;

/** Dedicated enable/disable transition of one endpoint; the only command that changes its status. */
public record SetEndpointStatusCommand(Revision expectedSourceRevision, EndpointId endpointId, EndpointStatus status) { }
