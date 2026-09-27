package com.cloud.gateway.domain.security.signature;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.SignatureContract;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Credential selection and replay identity are independent of secret/Redis implementations. */
public record SignatureAuthenticationRules(Duration maxClockSkew) {
    private static final String SEPARATOR = ":";

    public SignatureAuthenticationRules {
        Require.notNull(maxClockSkew, DomainException::missingField);
        Require.that(maxClockSkew, v -> !v.isNegative() && !v.isZero(), DomainException::invalidObjectField);
    }

    public Optional<Instant> acceptedTimestamp(SignatureInput evidence, Instant now) {
        try {
            Instant timestamp = Instant.ofEpochMilli(Long.parseLong(evidence.timestamp()));
            return Duration.between(timestamp, now).abs().compareTo(maxClockSkew) <= 0
                    ? Optional.of(timestamp) : Optional.empty();
        } catch (NumberFormatException invalidTimestamp) {
            return Optional.empty();
        }
    }

    public List<Credential> candidates(ClientId clientId, Credentials credentials, SignatureInput evidence, Instant now) {
        Credential active = credentials.activeCredential();
        List<Credential> candidates = new ArrayList<>();
        if (active.signatureContract() == SignatureContract.HTTP_REQUEST_SIGNATURE_V1) {
            if (!clientId.value().equals(evidence.clientId()) || !HttpRequestSignatureV1Verifier.valid(evidence)) {
                return List.of();
            }
            if (active.credentialId().value().equals(evidence.credentialId())) {
                if (active.validAt(now)) {
                    candidates.add(active);
                }
            } else {
                credentials.graceCredentialOptional()
                        .filter(value -> value.credentialId().value().equals(evidence.credentialId()) && value.validAt(now))
                        .ifPresent(candidates::add);
            }
        } else {
            if (active.validAt(now)) {
                candidates.add(active);
            }
            credentials.graceCredentialOptional().filter(value -> value.validAt(now)).ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    /** Preserves the existing evidence encoding before the technical irreversible digest. */
    public String replayIdentity(ClientId clientId, Credentials credentials, SignatureInput evidence) {
        String id = clientId.value();
        if (credentials.activeCredential().signatureContract() == SignatureContract.HTTP_REQUEST_SIGNATURE_V1) {
            return SignatureContract.HTTP_REQUEST_SIGNATURE_V1.name() + SEPARATOR
                    + id.length() + SEPARATOR + id + SEPARATOR + evidence.nonce();
        }
        return String.join(SEPARATOR, id, evidence.timestamp(), evidence.signature().toLowerCase(Locale.ROOT));
    }

    public Instant replayExpiresAt(Instant acceptedTimestamp) {
        return acceptedTimestamp.plus(maxClockSkew).plusMillis(1);
    }
}
