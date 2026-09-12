package com.cloud.gateway.domain.client;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

import java.util.Optional;

public record Credentials(
        Credential activeCredential,
        Credential graceCredential
) {
    public Credentials {
        Require.notNull(activeCredential, DomainException::missingField);
        if (graceCredential != null) {
            if (activeCredential.credentialId().equals(graceCredential.credentialId())) {
                throw invalid();
            }
            if (activeCredential.signatureContract() != graceCredential.signatureContract()) {
                throw invalid();
            }
        }
    }

    public Optional<Credential> graceCredentialOptional() {
        return Optional.ofNullable(graceCredential);
    }

    private static DomainException invalid() {
        return DomainException.invalidField();
    }
}
