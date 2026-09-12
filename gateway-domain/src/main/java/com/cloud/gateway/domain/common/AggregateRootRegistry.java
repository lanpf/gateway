package com.cloud.gateway.domain.common;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.AggregateRoot;
import com.cloud.framework.domain.EntityId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public abstract class AggregateRootRegistry<T extends AggregateRoot<ID>, ID extends EntityId<?>> {
    private final Map<ID, T> definitions;

    protected AggregateRootRegistry(Collection<T> aggregateRoots) {
        if (aggregateRoots == null) {
            definitions = Map.of();
        } else {
            Map<ID, T> indexed = new LinkedHashMap<>();

            aggregateRoots.forEach(aggregateRoot -> {
                Require.notNull(aggregateRoot, DomainException::invalidField);
                if (indexed.putIfAbsent(aggregateRoot.id(), aggregateRoot) != null) {
                    throw new DomainException(DomainError.ENDPOINT_AMBIGUOUS);
                }
            });
            definitions = Map.copyOf(indexed);
        }
    }


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
