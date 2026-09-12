package com.cloud.gateway.domain.security.signature;

import com.cloud.gateway.domain.client.SignatureContract;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public final class RequestSignatureVerifierRegistry {
    private final Map<SignatureContract, RequestSignatureVerifier> verifiers;

    public RequestSignatureVerifierRegistry(List<RequestSignatureVerifier> verifiers) {
        Require.notEmpty(verifiers, DomainException::missingField);
        Map<SignatureContract, RequestSignatureVerifier> indexed = new EnumMap<>(SignatureContract.class);
        verifiers.forEach(verifier -> {
            Require.notNull(verifier, DomainException::missingField);
            Require.that(verifier, v -> indexed.putIfAbsent(v.contract(), v) != null,
                    DomainException::invalidField);
        });
        Require.that(indexed.keySet(),
                v -> v.containsAll(EnumSet.allOf(SignatureContract.class)),
                DomainException::invalidField);
        this.verifiers = Map.copyOf(indexed);
    }

    public RequestSignatureVerifier required(SignatureContract contract) {
        return Require.notNull(verifiers.get(contract), DomainException::invalidField);
    }
}
