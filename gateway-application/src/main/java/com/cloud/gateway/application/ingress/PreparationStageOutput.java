package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.policy.execution.CapabilityExecution;
import java.util.Objects;

/** Prepared hands the access stage everything it consumes; Denied terminates at the edge. */
public sealed interface PreparationStageOutput {

    record Prepared(IngressSnapshot snapshot,
                    EndpointMatch endpointMatch,
                    RequestEvidence evidence,
                    CapabilityExecution execution) implements PreparationStageOutput {
        public Prepared {
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            Objects.requireNonNull(endpointMatch, "endpointMatch must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
            Objects.requireNonNull(execution, "execution must not be null");
        }
    }

    record Denied(CapabilityExecution execution) implements PreparationStageOutput {
        public Denied {
            Objects.requireNonNull(execution, "execution must not be null");
        }
    }
}
