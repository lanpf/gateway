package com.cloud.gateway.application.security;

import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.security.signature.RequestSignatureVerifierRegistry;
import com.cloud.gateway.domain.security.signature.SignatureAuthenticationRules;
import com.cloud.gateway.domain.security.signature.SignatureCryptography;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/** Coordinates dependencies; selection, accepted time and signed bytes are domain rules. */
@RequiredArgsConstructor
public final class RequestSignatureAuthenticationCommandService implements ClientCredentialAuthenticator {
    private final SecretResolver secrets;
    private final ReplayGuard replay;
    private final RequestSignatureVerifierRegistry verifiers;
    private final SignatureCryptography cryptography;
    private final SignatureAuthenticationRules rules;

    @Override
    public CompletionStage<ClientVerificationOutcome> authenticate(
            Credentials credentials, ClientSignatureEvidence evidence, Instant now) {
        Optional<Instant> timestamp = rules.acceptedTimestamp(evidence, now);
        if (timestamp.isEmpty()) {
            return completed(ClientVerificationOutcome.rejected());
        }
        for (Credential candidate : rules.candidates(credentials, evidence, now)) {
            String secret;
            try {
                secret = secrets.resolve(candidate.secretRef());
            } catch (RuntimeException ignored) {
                return completed(ClientVerificationOutcome.failed());
            }
            if (StringUtils.isBlank(secret)) {
                return completed(ClientVerificationOutcome.failed());
            }
            if (verifiers.required(candidate.signatureContract()).verify(secret, evidence)) {
                String identity = rules.replayIdentity(credentials, evidence);
                String replayKey = cryptography.digest(SignatureCryptography.Digest.SHA_256,
                        identity.getBytes(StandardCharsets.UTF_8));
                return register(candidate, replayKey, rules.replayExpiresAt(timestamp.get()), now);
            }
        }
        return completed(ClientVerificationOutcome.rejected());
    }

    private CompletionStage<ClientVerificationOutcome> register(
            Credential credential, String key, Instant expiresAt, Instant now) {
        CompletionStage<Boolean> registration;
        try {
            registration = replay.register(key, expiresAt);
        } catch (RuntimeException ignored) {
            return completed(ClientVerificationOutcome.failed());
        }
        if (registration == null) {
            return completed(ClientVerificationOutcome.failed());
        }
        CompletableFuture<ClientVerificationOutcome> outcome = new CompletableFuture<>();
        CompletableFuture<Boolean> pending = registration.toCompletableFuture();
        outcome.whenComplete((ignored, failure) -> {
            if (outcome.isCancelled()) {
                pending.cancel(true);
            }
        });
        pending.whenComplete((registered, failure) -> {
            if (pending.isCancelled() || failure instanceof CancellationException) {
                outcome.cancel(false);
            } else if (failure != null || registered == null) {
                outcome.complete(ClientVerificationOutcome.failed());
            } else if (registered) {
                outcome.complete(ClientVerificationOutcome.verified(
                        new VerifiedClient(
                                credential.credentialId(), credential.clientId(), ClientVerificationMode.REQUEST_SIGNATURE, now)));
            } else {
                outcome.complete(ClientVerificationOutcome.rejected());
            }
        });
        return outcome;
    }

    private static CompletionStage<ClientVerificationOutcome> completed(ClientVerificationOutcome outcome) {
        return CompletableFuture.completedFuture(outcome);
    }
}
