package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;

/** Canonical encoding contract. Ordering and textual protocol normalization are intentional. */
public interface CanonicalPolicyContentMapper {
    CanonicalIngressContent toCanonical(IngressContent source);
}
