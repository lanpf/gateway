package com.cloud.gateway.domain.policy.execution;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import java.util.EnumSet;
import java.util.Set;

/** A completed access check without a subject is distinct from a not-yet-checked request. */
public record CapabilityContext(CapabilitySlot slot, RequestEvidence evidence,
                                Endpoint endpoint, AccessGrantedContext granted) {
    public CapabilityContext {
        Require.notNull(slot, DomainException::missingField);
        Require.notNull(evidence, DomainException::missingField);
        if (slot == CapabilitySlot.POST_AUTH) {
            Require.notNull(endpoint, DomainException::missingField);
            Require.notNull(granted, DomainException::missingField);
        } else if (granted != null) {
            throw DomainException.invalidField();
        }
    }

    public Set<CapabilityFact> facts() {
        Set<CapabilityFact> facts = EnumSet.of(CapabilityFact.REQUEST);
        if (endpoint != null) {
            facts.add(CapabilityFact.ENDPOINT);
        }
        if (granted != null) {
            facts.add(CapabilityFact.ACCESS_GRANTED);
            if (granted.client() != null) {
                facts.add(CapabilityFact.VERIFIED_CLIENT);
            }
            if (granted.subject() != null) {
                facts.add(CapabilityFact.AUTHENTICATED_SUBJECT);
            }
        }
        return Set.copyOf(facts);
    }
}
