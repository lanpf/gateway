package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.access.IngressAccessDecision;
import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.policy.bundle.EffectivePolicy;
import com.cloud.gateway.domain.policy.capability.RateLimitDimension;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import java.util.Map;

/** Only an allowed decision carries executable post-authentication policy. */
public sealed interface IngressAccessOutput {
    record Denied(IngressAccessDecision.Denied decision) implements IngressAccessOutput { }

    record Allowed(IngressAccessGrantedContext context, EffectivePolicy policy, HeaderPolicy headers,
                   Map<RateLimitDimension, String> dimensions) implements IngressAccessOutput {
        public Allowed {
            dimensions = Map.copyOf(dimensions);
        }
    }
}
