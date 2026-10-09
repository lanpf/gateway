package com.cloud.gateway.domain.security.signature;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RequestSignatureVerifierRegistry {
    private final Map<SignatureContract, RequestSignatureVerifier> verifiers;

    public RequestSignatureVerifierRegistry(List<RequestSignatureVerifier> verifiers) {
        Require.notNull(verifiers);
        this.verifiers = NullSafe.copyOf(Require.noDuplicates(verifiers, RequestSignatureVerifier::contract));
        if (!this.verifiers.keySet().containsAll(EnumSet.allOf(SignatureContract.class))) {
            throw new DomainException(DomainError.SIGNATURE_VERIFIER_NOT_FOUND);
        }
    }

    public RequestSignatureVerifier required(SignatureContract contract) {
        return Optional.ofNullable(this.verifiers.get(contract))
                .orElseThrow(() -> new DomainException(DomainError.SIGNATURE_VERIFIER_NOT_FOUND));
    }
}
