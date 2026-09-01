package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public sealed interface SubjectAuthenticationResult {
    record Absent() implements SubjectAuthenticationResult { }

    record Authenticated(AuthenticatedSubject subject) implements SubjectAuthenticationResult {
        public Authenticated {
            Require.notNull(subject, DomainException::missingField);
        }
    }

    record Rejected() implements SubjectAuthenticationResult { }

    static SubjectAuthenticationResult absent() {
        return new Absent();
    }

    static SubjectAuthenticationResult authenticated(AuthenticatedSubject subject) {
        return new Authenticated(subject);
    }

    static SubjectAuthenticationResult rejected() {
        return new Rejected();
    }
}
