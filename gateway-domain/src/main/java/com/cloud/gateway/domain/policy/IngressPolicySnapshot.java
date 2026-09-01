package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public record IngressPolicySnapshot(
        long snapshotVersion,
        IngressPolicyContent content,
        String contentHash,
        Instant publishedAt
) {
    public IngressPolicySnapshot {
        if (snapshotVersion < 0) {
            throw invalid();
        }
        Require.notNull(content, DomainException::missingField);
        contentHash = Require.notBlank(contentHash, DomainException::missingField).trim();
        Require.notNull(publishedAt, DomainException::missingField);
        validate(content, publishedAt);
    }

    private static void validate(IngressPolicyContent content, Instant publishedAt) {
        content.clientAppPolicies().forEach((key, policy) -> {
            if (!key.equals(policy.clientAppId())) {
                throw invalid();
            }
        });
        content.channelAccessPolicies().forEach((key, policy) -> {
            if (!key.equals(policy.channelCode())) {
                throw invalid();
            }
            policy.allowedClientAppIds().forEach(clientAppId -> {
                if (!content.clientAppPolicies().containsKey(clientAppId)) {
                    throw invalid();
                }
            });
        });
        Set<ChannelCredentialId> credentialIds = new HashSet<>();
        content.channelCredentialPolicies().forEach((key, policy) -> {
            if (!key.equals(policy.channelCode())) {
                throw invalid();
            }
            if (!policy.activeCredential().validAt(publishedAt)) {
                throw invalid();
            }
            if (!credentialIds.add(policy.activeCredential().credentialId())) {
                throw invalid();
            }
            policy.graceCredentialOptional().ifPresent(credential -> {
                if (!credential.validAt(publishedAt)) {
                    throw invalid();
                }
                if (!credentialIds.add(credential.credentialId())) {
                    throw invalid();
                }
            });
        });
        content.routeAccessPolicies().forEach((key, policy) -> {
            if (!key.equals(policy.endpointId())) {
                throw invalid();
            }
            if (content.endpointCatalog().find(key).isEmpty()) {
                throw invalid();
            }
        });
    }

    private static DomainException invalid() {
        return new DomainException(DomainError.POLICY_SNAPSHOT_INVALID);
    }
}
