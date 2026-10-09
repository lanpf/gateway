package com.cloud.gateway.domain.capability.runtime;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;

import java.util.EnumSet;
import java.util.Set;
import com.cloud.framework.core.error.FrameworkException;

/**
 * What is known when a slot is planned. A completed access check without a subject is distinct from a
 * not-yet-checked request. Mandatory stage inputs are checked by construction; optional identity
 * availability is derived from the granted context, never supplied as an independent flag.
 */
public record CapabilityContext(CapabilitySlot slot,
                                RequestEvidence evidence,
                                Endpoint endpoint,
                                AccessGrantedContext granted) {
    public CapabilityContext {
        Require.allNotNull(slot, evidence);
        if (slot != CapabilitySlot.PRE_AUTH) {
            Require.allNotNull(endpoint, granted);
        } else if (granted != null) {
            throw FrameworkException.invalidObject();
        }
    }

    public Set<CapabilityFact> facts() {
        Set<CapabilityFact> facts = EnumSet.noneOf(CapabilityFact.class);
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
