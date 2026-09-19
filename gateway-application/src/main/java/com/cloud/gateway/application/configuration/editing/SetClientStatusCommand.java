package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientStatus;

/** Dedicated enable/disable transition of one client; the only command that changes its status. */
public record SetClientStatusCommand(Revision expectedSourceRevision, ClientId clientId, ClientStatus status) { }
