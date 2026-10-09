package com.cloud.gateway.domain.capability.policy;

import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import lombok.Getter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete capability policy configuration, with immutable bundle definitions and binding indexes.
 * The constructor builds both indexes from the entries' own identities, so an index key can never
 * disagree with the object it indexes.
 */
@Getter
public final class CapabilityPolicyConfig {

    private final Map<CapabilityBundleId, CapabilityBundle> bundles;
    private final Map<CapabilityBindingId, CapabilityBinding> bindings;

    /**
     * Indexes by entry identity: duplicate IDs are rejected even when the entries are equal, never
     * folded or overwritten; every binding must reference a bundle present in this policy configuration.
     * Both indexes are immutable copies detached from the caller.
     */
    public CapabilityPolicyConfig(Collection<CapabilityBundle> bundles, Collection<CapabilityBinding> bindings) {
        this.bundles = NullSafe.copyOf(Require.noDuplicates(bundles == null ? List.of() : bundles, CapabilityBundle::getBundleId,
                () -> new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID)));
        this.bindings = NullSafe.copyOf(Require.noDuplicates(bindings == null ? List.of() : bindings, CapabilityBinding::bindingId,
                () -> new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID)), (k, v) -> {
            if (!this.bundles.containsKey(v.bundleId())) {
                throw new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID);
            }
        });
    }

    public static CapabilityPolicyConfig empty() {
        return new CapabilityPolicyConfig(List.of(), List.of());
    }

    /** Equality and hashCode cover both full indexes and ignore input order. */
    @Override public boolean equals(Object other) {
        return this == other || other instanceof CapabilityPolicyConfig that
                && bundles.equals(that.bundles) && bindings.equals(that.bindings);
    }

    @Override public int hashCode() { return Objects.hash(bundles, bindings); }

    @Override public String toString() {
        return "CapabilityPolicyConfig[bundles=" + bundles + ", bindings=" + bindings + "]";
    }
}
