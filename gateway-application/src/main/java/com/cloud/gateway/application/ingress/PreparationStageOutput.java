package com.cloud.gateway.application.ingress;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.capability.runtime.CapabilityExecution;
import java.util.Objects;

/** Prepared hands the access stage everything it consumes; Denied terminates at the edge. */
public sealed interface PreparationStageOutput {

    record Prepared(IngressSnapshot snapshot,
                    EndpointMatch endpointMatch,
                    RequestEvidence evidence,
                    CapabilityExecution execution) implements PreparationStageOutput {
        public Prepared {
            Require.allNotNull(snapshot, endpointMatch, evidence, execution);
        }
    }

    record Denied(CapabilityExecution execution) implements PreparationStageOutput {
        public Denied {
            Require.notNull(execution);
        }
    }
}
