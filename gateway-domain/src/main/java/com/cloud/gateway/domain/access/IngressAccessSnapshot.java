package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.client.CredentialId;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record IngressAccessSnapshot(
        AccessSnapshotVersion version,
        IngressAccessContent content,
        String contentHash,
        Instant publishedAt
) {
    public IngressAccessSnapshot {
        Require.notNull(version, DomainException::missingField);
        Require.notNull(content, DomainException::missingField);
        contentHash = Require.notBlank(contentHash, DomainException::missingField).trim();
        Require.notNull(publishedAt, DomainException::missingField);
        validate(content);
        validateCredentials(content, publishedAt);
    }


    private static void validate(IngressAccessContent content) {
        content.clientRegistry().values().stream()
                .flatMap(client -> client.access().allowedApps().stream())
                .forEach(appId -> {
                    if (content.appRegistry().find(appId).isEmpty()) {
                        throw invalid();
                    }
                });

    }

    private static void validateCredentials(IngressAccessContent content, Instant publishedAt) {
        Set<CredentialId> indexed = new HashSet<>();
        content.clientRegistry().values().stream()
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

//    /** Pure version/content admission; source revisions and atomic replacement belong to adapters. */
//    public Admission admissionOf(IngressAccessSnapshot candidate) {
//        Require.notNull(candidate, DomainException::missingField);
//        if (candidate.version() < version) {
//            return Admission.STALE;
//        }
//        if (candidate.version() == version) {
//            return contentHash.equals(candidate.contentHash()) ? Admission.UNCHANGED : Admission.CONFLICT;
//        }
//        candidate.content().validateAppUpdatesFrom(content);
//        return Admission.ADVANCE;
//    }
//
//    public enum Admission { STALE, UNCHANGED, CONFLICT, ADVANCE }

    private static DomainException invalid() {
        return new DomainException(DomainError.ACCESS_SNAPSHOT_INVALID);
    }
}
