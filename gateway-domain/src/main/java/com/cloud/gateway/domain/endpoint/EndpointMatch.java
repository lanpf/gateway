package com.cloud.gateway.domain.endpoint;


import com.cloud.framework.core.validation.Require;
import com.cloud.framework.core.util.NullSafe;

import java.util.Collection;

/**
 * Classification of transport-matched candidates; path grammar is an adapter fact, the winner
 * choice is this domain rule. Identical candidates collapse into one — an endpoint matched by
 * several of its own matchers arrives as [E, E]. The same id with divergent content, or two
 * different endpoints, is ambiguous and leaks no endpoint to access.
 */
public sealed interface EndpointMatch {

    record Unique(Endpoint endpoint) implements EndpointMatch {
        public Unique {
            Require.notNull(endpoint);
        }
    }

    record None() implements EndpointMatch { }

    /** No endpoint leaks to access from ambiguity or unknown paths; the evaluator denies uniformly. */
    record Ambiguous() implements EndpointMatch { }

    static EndpointMatch of(Collection<Endpoint> candidates) {
        Require.notNull(candidates);
        NullSafe.forEach(candidates);
        var checked = java.util.List.copyOf(candidates);
        if (checked.isEmpty()) {
            return new None();
        }
        Endpoint first = checked.get(0);
        return checked.stream().allMatch(first::sameDefinition) ? new Unique(first) : new Ambiguous();
    }
}
