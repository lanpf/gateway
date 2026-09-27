package com.cloud.gateway.domain.common;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.AggregateRoot;
import com.cloud.framework.domain.EntityId;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AggregateRootRegistry<T extends AggregateRoot<ID>, ID extends EntityId<?>> {
    private final Map<ID, T> definitions;

    protected AggregateRootRegistry(Collection<T> aggregateRoots) {
        definitions = aggregateRoots == null
                ? Map.of()
                : aggregateRoots.stream()
                        .map(value -> Require.notNull(value, DomainException::missingField))
                        .collect(Collectors.toUnmodifiableMap(
                                value -> Require.notNull(value.getId(), DomainException::missingField),
                                Function.identity(), (left, right) -> { throw ambiguous(); }));
    }

    protected DomainException ambiguous() {
        return DomainException.invalidObjectField();
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
