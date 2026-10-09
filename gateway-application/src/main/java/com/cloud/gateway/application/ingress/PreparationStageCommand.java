package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import java.util.Objects;

/** The only input of the prepare stage: request facts captured once at the edge. */
public record PreparationStageCommand(RequestEvidence evidence) {
    public PreparationStageCommand {
        Require.notNull(evidence);
    }
}
