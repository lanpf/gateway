package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.access.RequestEvidence;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import java.util.Objects;

/** Access-stage input, constructed from the prepared output; nothing is re-collected here. */
public record IngressAccessCommand(
        IngressAccessSnapshot snapshot,
        EndpointMatch endpointMatch,
        RequestEvidence evidence
) {
    public IngressAccessCommand {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(endpointMatch, "endpointMatch must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
    }
}
