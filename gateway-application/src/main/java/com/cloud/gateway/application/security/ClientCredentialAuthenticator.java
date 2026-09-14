package com.cloud.gateway.application.security;

import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.Credentials;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

public interface ClientCredentialAuthenticator {
    /**
     * Returns Rejected for invalid evidence or replay, and Unavailable when a
     * required verification dependency cannot complete. Unavailable is not anonymous access.
     */
    CompletionStage<ClientVerificationOutcome> authenticate(
            Credentials credentials,
            ClientId clientId,
            ClientSignatureEvidence evidence,
            Instant now);
}
