package com.cloud.gateway.domain.client;

import com.cloud.framework.core.validation.Require;

import java.time.Instant;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

public record Credential(
        CredentialId credentialId,
        String secretRef,
        SignatureContract signatureContract,
        Instant validFrom,
        Instant expiresAt
        ) {
    public Credential {
        Require.allNotNull(credentialId, signatureContract, validFrom, expiresAt);
        secretRef = Require.notBlank(secretRef).trim();
        if (!validFrom.isBefore(expiresAt)) {
            throw new DomainException(DomainError.CLIENT_CREDENTIAL_INVALID);
        }
    }

    public boolean validAt(Instant now) {
        return !now.isBefore(validFrom) && now.isBefore(expiresAt);
    }
}
