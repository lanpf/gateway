package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialId;
import java.time.Instant;

public record VerifiedChannelCredential(
        ChannelCredentialAuthenticationMethod authenticationMethod,
        ChannelCredentialId credentialId,
        ChannelCode channelCode,
        Instant verifiedAt
) {
    public VerifiedChannelCredential {
        Require.notNull(authenticationMethod, DomainException::missingField);
        Require.notNull(credentialId, DomainException::missingField);
        Require.notNull(channelCode, DomainException::missingField);
        Require.notNull(verifiedAt, DomainException::missingField);
    }
}
