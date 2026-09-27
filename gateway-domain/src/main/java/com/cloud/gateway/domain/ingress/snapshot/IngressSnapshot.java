package com.cloud.gateway.domain.ingress.snapshot;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record IngressSnapshot(
        SnapshotVersion version,
        IngressContent content,
        String contentHash,
        Instant publishedAt
) {
    public IngressSnapshot {
        Require.notNull(version, DomainException::missingField);
        Require.notNull(content, DomainException::missingField);
        contentHash = Require.notBlank(contentHash, DomainException::invalidObjectField).trim();
        Require.notNull(publishedAt, DomainException::missingField);
        validate(content);
        validateCredentials(content, publishedAt);
    }


    private static void validate(IngressContent content) {
        content.clients().values().stream()
                .flatMap(client -> client.access().allowedApps().stream())
                .forEach(appId -> {
                    if (content.apps().find(appId).isEmpty()) {
                        throw invalid();
                    }
                });

    }

    private static void validateCredentials(IngressContent content, Instant publishedAt) {
        Set<CredentialId> indexed = new HashSet<>();
        content.clients().values().stream()
                .map(client -> client.access().credentials())
                .filter(Objects::nonNull)
                .forEach(credentials -> {
                    if (!credentials.activeCredential().validAt(publishedAt)) {
                        throw invalid();
                    }
                    if (!indexed.add(credentials.activeCredential().credentialId())) {
                        throw invalid();
                    }
                    credentials.graceCredentialOptional().ifPresent(credential -> {
                        if (!credential.validAt(publishedAt)) {
                            throw invalid();
                        }
                        if (!indexed.add(credential.credentialId())) {
                            throw invalid();
                        }
                    });
                });
    }

    /** Pure version/content acceptance; source revisions and atomic replacement belong to adapters. */
    public Acceptance acceptanceOf(IngressSnapshot candidate) {
        Require.notNull(candidate, DomainException::missingField);
        if (candidate.version().value() < version.value()) {
            return Acceptance.STALE;
        }
        if (candidate.version().equals(version)) {
            return contentHash.equals(candidate.contentHash()) ? Acceptance.UNCHANGED : Acceptance.CONFLICT;
        }
        return Acceptance.ADVANCE;
    }

    public enum Acceptance { STALE, UNCHANGED, CONFLICT, ADVANCE }

    private static DomainException invalid() {
        return new DomainException(DomainError.ACCESS_SNAPSHOT_INVALID);
    }
}
