package com.cloud.gateway.domain.common;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.AggregateRoot;
import com.cloud.framework.domain.EntityId;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractAggregateRootRegistry<T extends AggregateRoot<ID>, ID extends EntityId<?>> {
    private final Map<ID, T> definitions;

    protected AbstractAggregateRootRegistry(Collection<T> aggregateRoots) {
        definitions = aggregateRoots == null
                ? Map.of()
                : Map.copyOf(Require.noDuplicates(aggregateRoots, AggregateRoot::getId, this::ambiguous));
    }

    /** All concrete registries own an aggregate-specific ambiguity code; no generic default remains. */
    protected abstract DomainException ambiguous();

    public Optional<T> find(ID id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Collection<T> values() {
        return definitions.values();
    }

    public Map<ID, T> asMap() {
        return definitions;
    }
}
