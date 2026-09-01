package com.cloud.gateway.api.policy;

import com.cloud.gateway.api.enums.SignaturePayloadContractApiEnum;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ChannelCredentialApiCommand(
        @NotBlank String credentialId,
        @NotNull Instant validFrom,
        @NotNull @Future Instant expiresAt,
        @NotNull SignaturePayloadContractApiEnum signaturePayloadContract,
        @NotBlank String secretRef
) { }
