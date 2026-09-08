package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public sealed interface ChannelCredentialAuthenticationOutcome {
    record Absent() implements ChannelCredentialAuthenticationOutcome { }

    record Authenticated(VerifiedChannelCredential credential) implements ChannelCredentialAuthenticationOutcome {
        public Authenticated {
            Require.notNull(credential, DomainException::missingField);
        }
    }

    record Rejected() implements ChannelCredentialAuthenticationOutcome { }

    static ChannelCredentialAuthenticationOutcome absent() {
        return new Absent();
    }

    static ChannelCredentialAuthenticationOutcome authenticated(VerifiedChannelCredential credential) {
        return new Authenticated(credential);
    }

    static ChannelCredentialAuthenticationOutcome rejected() {
        return new Rejected();
    }
}
