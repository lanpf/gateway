package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public sealed interface SubjectAuthenticationOutcome {
    record Absent() implements SubjectAuthenticationOutcome { }

    record Authenticated(AuthenticatedSubject subject) implements SubjectAuthenticationOutcome {
        public Authenticated {
            Require.notNull(subject, DomainException::missingField);
        }
    }

    record Rejected() implements SubjectAuthenticationOutcome { }

    record Unavailable(SubjectAuthenticationUnavailableReason reason) implements SubjectAuthenticationOutcome {
        public Unavailable {
            Require.notNull(reason, DomainException::missingField);
        }
    }

    static SubjectAuthenticationOutcome absent() {
        return new Absent();
    }

    static Authenticated authenticated(AuthenticatedSubject subject) {
        return new Authenticated(subject);
    }

    static SubjectAuthenticationOutcome rejected() {
        return new Rejected();
    }

    static SubjectAuthenticationOutcome unavailable(SubjectAuthenticationUnavailableReason reason) {
        return new Unavailable(reason);
    }
}
