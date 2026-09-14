package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.access.RequestEvidence;
import java.util.Objects;

/** The only input of the prepare stage: request facts captured once at the edge. */
public record PrepareIngressCommand(RequestEvidence evidence) {
    public PrepareIngressCommand {
        Objects.requireNonNull(evidence, "evidence must not be null");
    }
}
