package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public sealed interface ChannelCredentialAuthenticationResult {
    record Absent() implements ChannelCredentialAuthenticationResult { }

    record Authenticated(VerifiedChannelCredential credential) implements ChannelCredentialAuthenticationResult {
        public Authenticated {
            Require.notNull(credential, DomainException::missingField);
        }
    }

    record Rejected() implements ChannelCredentialAuthenticationResult { }

    static ChannelCredentialAuthenticationResult absent() {
        return new Absent();
    }

    static ChannelCredentialAuthenticationResult authenticated(VerifiedChannelCredential credential) {
        return new Authenticated(credential);
    }

    static ChannelCredentialAuthenticationResult rejected() {
        return new Rejected();
    }
}
