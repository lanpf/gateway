package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import java.util.Objects;

/**
 * Access-stage input: the prepare stage's output plus the signature evidence collected after the
 * endpoint was matched. Whether to buffer a body depends on that endpoint's policy, so the evidence
 * cannot come from prepare; a null value means the request submitted no signature. Wrapping the
 * prepared output rather than restating its fields keeps the stage-to-stage handoff typed — the
 * access command cannot be built from anything other than what prepare actually produced.
 */
public record AccessStageCommand(
        PreparationStageOutput.Prepared prepared,
        ClientSignatureEvidence signatureEvidence
) {
    public AccessStageCommand {
        Require.notNull(prepared);
    }
}
