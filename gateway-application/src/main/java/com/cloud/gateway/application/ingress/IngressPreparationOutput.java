package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.access.RequestEvidence;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.policy.capability.QuotaExecution;
import com.cloud.gateway.domain.policy.capability.RateLimitDecision;
import java.util.Objects;

/** Prepared hands the access stage everything it consumes; Denied terminates at the edge. */
public sealed interface IngressPreparationOutput {

    record Prepared(IngressAccessSnapshot snapshot,
                    EndpointMatch endpointMatch,
                    RequestEvidence evidence,
                    QuotaExecution quota) implements IngressPreparationOutput {
        public Prepared {
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            Objects.requireNonNull(endpointMatch, "endpointMatch must not be null");
            Objects.requireNonNull(evidence, "evidence must not be null");
            Objects.requireNonNull(quota, "quota must not be null");
        }
    }

    record Denied(RateLimitDecision decision) implements IngressPreparationOutput {
        public Denied {
            Objects.requireNonNull(decision, "decision must not be null");
        }
    }
}
