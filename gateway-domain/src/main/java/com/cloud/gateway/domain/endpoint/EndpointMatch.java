package com.cloud.gateway.domain.endpoint;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Classification of transport-matched candidates; path grammar is an adapter fact, the winner
 * choice is this domain rule. Identical candidates collapse into one — an endpoint matched by
 * several of its own matchers arrives as [E, E]. The same id with divergent content, or two
 * different endpoints, is ambiguous and leaks no endpoint to access.
 */
public sealed interface EndpointMatch {

    record Unique(Endpoint endpoint) implements EndpointMatch {
        public Unique {
            Require.notNull(endpoint, DomainException::missingField);
        }
    }

    record None() implements EndpointMatch { }

    /** No endpoint leaks to access from ambiguity or unknown paths; the evaluator denies uniformly. */
    record Ambiguous() implements EndpointMatch { }

    static EndpointMatch of(Collection<Endpoint> candidates) {
        Require.notNull(candidates, DomainException::missingField);

        Map<EndpointId, Endpoint> indexed = new LinkedHashMap<>();
        for (Endpoint candidate : candidates) {
            Require.notNull(candidate, DomainException::missingField);
            Endpoint present = indexed.putIfAbsent(candidate.id(), candidate);
            if (present != null && !present.equals(candidate) || indexed.size() > 1) {
                return new Ambiguous();
            }
        }

        return indexed.isEmpty() ? new None() : new Unique(indexed.values().iterator().next());
    }
}
