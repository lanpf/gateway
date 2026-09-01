package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialId;
import com.cloud.gateway.domain.policy.ClientAppId;
import java.time.Instant;
import java.util.Set;

public record TrustedRequestContext(
        ClientContext clientContext,
        ChannelContext channelContext,
        ChannelCredentialContext channelCredentialContext,
        SubjectContext subjectContext,
        long policySnapshotVersion
) {
    public TrustedRequestContext {
        Require.notNull(clientContext, DomainException::missingField);
    }

    public record ClientContext(ClientAppId clientAppId, String platform) {
        public ClientContext {
            Require.notNull(clientAppId, DomainException::missingField);
            platform = Require.notBlank(platform, DomainException::missingField).trim();
        }
    }

    public record ChannelContext(ChannelCode channelCode) {
        public ChannelContext {
            Require.notNull(channelCode, DomainException::missingField);
        }
    }

    public record ChannelCredentialContext(ChannelCredentialId credentialId, ChannelCode channelCode) {
        public ChannelCredentialContext {
            Require.notNull(credentialId, DomainException::missingField);
            Require.notNull(channelCode, DomainException::missingField);
        }
    }

    public record SubjectContext(
            SubjectType subjectType,
            String subjectId,
            String loginSessionId,
            Set<String> audiences,
            Set<String> scopes,
            Instant credentialExpiresAt
    ) {
        public SubjectContext {
            Require.notNull(subjectType, DomainException::missingField);
            subjectId = Require.notBlank(subjectId, DomainException::missingField).trim();
            loginSessionId = Require.notBlank(loginSessionId, DomainException::missingField).trim();
            audiences = Set.copyOf(Require.notNull(audiences, DomainException::missingField));
            scopes = Set.copyOf(Require.notNull(scopes, DomainException::missingField));
            Require.notNull(credentialExpiresAt, DomainException::missingField);
        }
    }
}
