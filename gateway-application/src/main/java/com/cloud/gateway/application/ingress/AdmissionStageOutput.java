package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.policy.execution.CapabilityExecution;
import com.cloud.gateway.domain.policy.bundle.ResolvedCapabilities;
import java.util.Objects;

/** Admission is evaluated only for allowed access; capability denial is a completed evaluation. */
public record AdmissionStageOutput(AccessStageOutput.Allowed access, ResolvedCapabilities capabilities,
                                   CapabilityExecution execution) {
    public AdmissionStageOutput {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(execution, "execution");
    }
}
