package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import java.util.Objects;

/** Captured request facts; the snapshot is supplied once by the protocol adapter. */
public record IngressAccessCommand(
        IngressAccessSnapshot snapshot,
        Endpoint endpoint, AppId appId, ClientId clientId,
        String origin, boolean unsafe, String remoteIp
) {
    public IngressAccessCommand {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(remoteIp, "remoteIp must not be null");
    }
}
