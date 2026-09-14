package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientStatus;

/** Dedicated enable/disable transition of one client; the only command that changes its status. */
public record SetClientStatusCommand(String expectedSourceRevision, ClientId clientId, ClientStatus status) { }
