package com.cloud.gateway.api.policy;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.ClientStatusApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;

public record SaveClientApiCommand(
        @NotBlank String expectedSourceRevision,
        @NotBlank String clientId,
        @NotNull ClientStatusApiEnum status,
        Set<@NotBlank String> allowedAppIds,
        @Valid ClientCredential activeCredential,
        @Valid ClientCredential graceCredential
        ) implements Request {
    public SaveClientApiCommand {
        allowedAppIds = allowedAppIds == null ? null : Set.copyOf(allowedAppIds);
    }

    public record ClientCredential(
            @NotBlank String credentialId,
            @NotBlank String secretRef,
            @NotNull SignatureContractApiEnum signatureContract,
            @NotNull Instant validFrom,
            @NotNull @Future Instant expiresAt
    ) { }
}
