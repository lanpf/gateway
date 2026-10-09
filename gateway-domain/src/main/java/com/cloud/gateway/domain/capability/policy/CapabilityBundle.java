package com.cloud.gateway.domain.capability.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.capability.CapabilityConfig;
import com.cloud.gateway.domain.capability.CapabilityId;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;

/**
 * Immutable open configuration collection; one node per capability, never an execution chain. The
 * only constructor receives the raw entries and indexes them by their own identities.
 */
@Getter
public final class CapabilityBundle {

    private final CapabilityBundleId bundleId;
    private final Map<CapabilityId, CapabilityConfig> configs;

    /**
     * Indexes by entry identity: duplicate capability IDs are rejected even when the entries are
     * equal, never folded or overwritten; the bundle ID and every entry and entry ID must be
     * non-null. The index is an immutable copy detached from the caller.
     */
    public CapabilityBundle(CapabilityBundleId bundleId, Collection<? extends CapabilityConfig> configs) {
        Require.allNotNull(bundleId, configs);
        this.bundleId = bundleId;
        this.configs = Map.copyOf(Require.noDuplicates(configs, CapabilityConfig::id,
                () -> new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID)));
    }

    public <C extends CapabilityConfig> Optional<C> find(CapabilityId id, Class<C> configType) {
        Require.allNotNull(id, configType);
        return Optional.ofNullable(configs.get(id))
                .map(config -> CapabilityConfig.requireType(id, configType, config));
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof CapabilityBundle that
                && bundleId.equals(that.bundleId) && configs.equals(that.configs);
    }

    @Override public int hashCode() { return Objects.hash(bundleId, configs); }

    @Override public String toString() {
        return "CapabilityBundle[bundleId=" + bundleId + ", configs=" + configs + "]";
    }
}
