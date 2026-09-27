package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import com.cloud.gateway.domain.capability.configuration.EffectiveCapabilityConfig;
import java.util.Objects;

/** Admission is evaluated only for allowed access; capability denial is a completed evaluation. */
public record AdmissionStageOutput(AccessStageOutput.Allowed access, EffectiveCapabilityConfig capabilities,
                                   CapabilityExecution execution) {
    public AdmissionStageOutput {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(execution, "execution");
    }
}
