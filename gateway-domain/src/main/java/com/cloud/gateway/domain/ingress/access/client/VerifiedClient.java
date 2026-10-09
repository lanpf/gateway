package com.cloud.gateway.domain.ingress.access.client;


import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.CredentialId;

import java.time.Instant;

public record VerifiedClient(
        ClientId clientId,
        CredentialId credentialId,
        ClientVerificationMode verificationMode,
        Instant verifiedAt
) {
    public VerifiedClient {
        Require.allNotNull(clientId, credentialId, verificationMode, verifiedAt);
    }
}
