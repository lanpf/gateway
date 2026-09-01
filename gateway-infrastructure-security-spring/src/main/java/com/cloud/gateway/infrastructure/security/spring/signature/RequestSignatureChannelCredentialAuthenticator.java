package com.cloud.gateway.infrastructure.security.spring.signature;

import com.cloud.gateway.application.security.ChannelCredentialAuthenticator;
import com.cloud.gateway.application.security.ChannelSignatureEvidence;
import com.cloud.gateway.application.security.ReplayGuard;
import com.cloud.gateway.application.security.SecretResolver;
import com.cloud.gateway.domain.access.ChannelCredentialAuthenticationMethod;
import com.cloud.gateway.domain.access.ChannelCredentialAuthenticationResult;
import com.cloud.gateway.domain.access.VerifiedChannelCredential;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.domain.policy.RequestSignatureChannelCredential;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
public final class RequestSignatureChannelCredentialAuthenticator implements ChannelCredentialAuthenticator {
    private static final char REPLAY_KEY_SEPARATOR = ':';
    private final SecretResolver secretResolver;
    private final ReplayGuard replayGuard;
    private final RequestSignatureVerifierRegistry verifierRegistry;
    private final Duration maxClockSkew;

    @Override
    public CompletionStage<ChannelCredentialAuthenticationResult> authenticate(
            IngressChannelCredentialPolicy policy, ChannelSignatureEvidence evidence, Instant now) {
        Instant requestTimestamp = parseTimestamp(evidence.timestamp());
        if (requestTimestamp == null
                || Duration.between(requestTimestamp, now).abs().compareTo(maxClockSkew) > 0) {
            return CompletableFuture.completedFuture(ChannelCredentialAuthenticationResult.rejected());
        }
        Optional<RequestSignatureChannelCredential> matched = verify(policy.activeCredential(), evidence, now)
                ? Optional.of(policy.activeCredential())
                : policy.graceCredentialOptional().filter(value -> verify(value, evidence, now));
        if (matched.isEmpty()) {
            return CompletableFuture.completedFuture(ChannelCredentialAuthenticationResult.rejected());
        }
        String replayKey = RequestSignatureCryptography.digest(RequestSignatureCryptography.SHA_256,
                policy.channelCode().value() + REPLAY_KEY_SEPARATOR
                        + evidence.timestamp() + REPLAY_KEY_SEPARATOR
                        + evidence.signature());
        RequestSignatureChannelCredential credential = matched.orElseThrow();
        return replayGuard.register(replayKey, now.plus(maxClockSkew)).thenApply(registered -> registered
                ? ChannelCredentialAuthenticationResult.authenticated(new VerifiedChannelCredential(
                        ChannelCredentialAuthenticationMethod.REQUEST_SIGNATURE,
                        credential.credentialId(), credential.channelCode(), now))
                : ChannelCredentialAuthenticationResult.rejected());
    }

    private static Instant parseTimestamp(String timestamp) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(timestamp));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean verify(RequestSignatureChannelCredential credential, ChannelSignatureEvidence evidence, Instant now) {
        if (!credential.validAt(now)) {
            return false;
        }
        String secret = secretResolver.resolve(credential.secretRef());
        RequestSignatureVerifier verifier = verifierRegistry.required(credential.signaturePayloadContract());
        return verifier.verify(secret, evidence);
    }
}
