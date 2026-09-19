package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Whole-pair rotation of one client's signing credentials; null graceCredential clears it.
 * Requires the client to exist; the pair must share one signature contract and distinct credentialIds.
 * <p>expectedSourceRevision follows the same opaque CAS token convention as the other saves,
 * sharing the client-policies definition revision with access-settings edits.
 */
public record SaveClientCredentialApiCommand(
        String expectedSourceRevision,
        @NotBlank String clientId,
        @NotNull @Valid CredentialApiCommand activeCredential,
        @Valid CredentialApiCommand graceCredential
) implements Request {

    /** One signing credential; the secret itself is resolved from secretRef and never transported. */
    public record CredentialApiCommand(
            @NotBlank String credentialId,
            @NotBlank String secretRef,
            @NotNull SignatureContractApiEnum signatureContract,
            @NotNull Instant validFrom,
            @NotNull @Future Instant expiresAt
    ) { }
}
