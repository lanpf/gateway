package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.validation.Require;
import java.util.Objects;

/** Access success carries the preparation execution ledger; denial never enters admission. */
public record AdmissionStageCommand(AccessStageOutput.Allowed access) {
    public AdmissionStageCommand {
        Require.notNull(access);
    }
}
