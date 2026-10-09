package com.cloud.gateway.domain.capability.policy;

import com.cloud.framework.core.validation.Require;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.cloud.framework.core.error.FrameworkException;

/** Immutable request-local policies indexed by their declared policy type, independent of producers. */
public final class EffectiveCapabilityPolicies {
    private final Map<Class<?>, Value<?>> values;

    public EffectiveCapabilityPolicies(Collection<Value<?>> values) {
        this.values = Map.copyOf(Require.noDuplicates(values, Value::policyType));
    }

    public <P> P get(Class<P> policyType) {
        Require.notNull(policyType);
        Value<?> value = Optional.ofNullable(values.get(policyType)).orElseThrow(FrameworkException::invalidObject);
        return policyType.cast(value.policy());
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof EffectiveCapabilityPolicies that && values.equals(that.values);
    }

    @Override public int hashCode() { return values.hashCode(); }

    public record Value<P>(Class<P> policyType, P policy) {
        public Value {
            Require.allNotNull(policyType, policy);
            if (!policyType.isInstance(policy)) {
                throw FrameworkException.invalidObject();
            }
        }
    }
}
