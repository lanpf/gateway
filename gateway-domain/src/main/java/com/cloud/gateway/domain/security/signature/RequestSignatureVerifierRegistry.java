package com.cloud.gateway.domain.security.signature;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RequestSignatureVerifierRegistry {
    private final Map<SignatureContract, RequestSignatureVerifier> verifiers;

    public RequestSignatureVerifierRegistry(List<RequestSignatureVerifier> verifiers) {
        Require.notEmpty(verifiers, DomainException::invalidObjectField);
        Map<SignatureContract, RequestSignatureVerifier> indexed = verifiers.stream()
                .map(value -> Require.notNull(value, DomainException::missingField))
                .collect(Collectors.toUnmodifiableMap(
                        value -> Require.notNull(value.contract(), DomainException::missingField),
                        Function.identity(), (left, right) -> { throw DomainException.invalidObjectField(); }));
        Require.that(indexed.keySet(), v -> v.containsAll(EnumSet.allOf(SignatureContract.class)), DomainException::invalidObjectField);
        this.verifiers = Map.copyOf(indexed);
    }

    public RequestSignatureVerifier required(SignatureContract contract) {
        return Require.notNull(verifiers.get(contract), () -> new DomainException(DomainError.SIGNATURE_VERIFIER_NOT_FOUND));
    }
}
