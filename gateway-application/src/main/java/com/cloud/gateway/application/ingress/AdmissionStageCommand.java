package com.cloud.gateway.application.ingress;

import java.util.Objects;

/** Access success carries the preparation execution ledger; denial never enters admission. */
public record AdmissionStageCommand(AccessStageOutput.Allowed access) {
    public AdmissionStageCommand {
        Objects.requireNonNull(access, "access");
    }
}
