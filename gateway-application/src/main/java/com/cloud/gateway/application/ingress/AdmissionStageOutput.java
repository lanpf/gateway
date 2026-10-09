package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import com.cloud.gateway.domain.capability.policy.EffectiveCapabilityPolicies;
import java.util.Objects;

/** Admission is evaluated only for allowed access; capability denial is a completed evaluation. */
public record AdmissionStageOutput(AccessStageOutput.Allowed access, EffectiveCapabilityPolicies capabilities,
                                   CapabilityExecution execution) {
    public AdmissionStageOutput {
        Require.allNotNull(access, capabilities, execution);
    }
}
