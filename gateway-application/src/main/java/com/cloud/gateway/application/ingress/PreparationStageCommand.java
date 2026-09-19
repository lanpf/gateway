package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import java.util.Objects;

/** The only input of the prepare stage: request facts captured once at the edge. */
public record PreparationStageCommand(RequestEvidence evidence) {
    public PreparationStageCommand {
        Objects.requireNonNull(evidence, "evidence must not be null");
    }
}
