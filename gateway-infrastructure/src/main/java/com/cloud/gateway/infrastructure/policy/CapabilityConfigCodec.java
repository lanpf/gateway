package com.cloud.gateway.infrastructure.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.CapabilityConfig;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyComposer;
import java.util.Objects;
import java.util.function.Function;

/** Registered wire type and semantic construction; no polymorphic class names are accepted from input. */
public record CapabilityConfigCodec<C extends CapabilityConfig, S>(
        CapabilityPolicyComposer<C, ?> composer, Class<S> storedType,
        Function<S, C> decode, Function<C, S> encode) {
    public CapabilityConfigCodec {
        Require.allNotNull(composer, storedType, decode, encode);
    }
}
