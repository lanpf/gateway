package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.time.Instant;

public record RequestSignatureChannelCredential(
        ChannelCredentialId credentialId,
        Instant validFrom,
        Instant expiresAt,
        ChannelCode channelCode,
        SignaturePayloadContract signaturePayloadContract,
        String secretRef
) {
    public RequestSignatureChannelCredential {
        Require.notNull(credentialId, DomainException::missingField);
        Require.notNull(validFrom, DomainException::missingField);
        Require.notNull(expiresAt, DomainException::missingField);
        if (!expiresAt.isAfter(validFrom)) {
            throw DomainException.invalidField();
        }
        Require.notNull(channelCode, DomainException::missingField);
        Require.notNull(signaturePayloadContract, DomainException::missingField);
        secretRef = Require.notBlank(secretRef, DomainException::missingField).trim();
    }

    public boolean validAt(Instant now) {
        Require.notNull(now, DomainException::missingField);
        return !now.isBefore(validFrom) && now.isBefore(expiresAt);
    }
}
