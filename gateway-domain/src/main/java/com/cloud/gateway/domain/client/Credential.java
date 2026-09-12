package com.cloud.gateway.domain.client;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

import java.time.Instant;

public record Credential(
        CredentialId credentialId,
        String secretRef,
        SignatureContract signatureContract,
        Instant validFrom,
        Instant expiresAt
        ) {
    public Credential {
        Require.notNull(credentialId, DomainException::missingField);
        Require.notNull(signatureContract, DomainException::missingField);
        secretRef = Require.notBlank(secretRef, DomainException::missingField).trim();
        Require.notNull(validFrom, DomainException::missingField);
        Require.notNull(expiresAt, DomainException::missingField);
        if (!validFrom.isBefore(expiresAt)) {
            throw DomainException.invalidField();
        }
    }

    public boolean validAt(Instant now) {
        return !now.isBefore(validFrom) && now.isBefore(expiresAt);
    }
}
