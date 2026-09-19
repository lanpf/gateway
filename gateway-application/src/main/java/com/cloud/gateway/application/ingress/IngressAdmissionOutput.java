package com.cloud.gateway.application.ingress;

import java.util.Objects;

/** Terminal orchestration output: access rejection or a completed admission evaluation. */
public sealed interface IngressAdmissionOutput {
    record AccessDenied(AccessStageOutput.Denied access) implements IngressAdmissionOutput {
        public AccessDenied {
            Objects.requireNonNull(access, "access");
        }
    }

    /** Evaluation completed; its quota can still deny forwarding. */
    record Evaluated(AdmissionStageOutput admission) implements IngressAdmissionOutput {
        public Evaluated {
            Objects.requireNonNull(admission, "admission");
        }
    }
}
