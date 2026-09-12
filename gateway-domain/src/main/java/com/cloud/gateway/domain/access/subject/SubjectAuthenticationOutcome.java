package com.cloud.gateway.domain.access.subject;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public sealed interface SubjectAuthenticationOutcome {

    record Authenticated(AuthenticatedSubject subject) implements SubjectAuthenticationOutcome {
        public Authenticated {
            Require.notNull(subject, DomainException::missingField);
        }
    }

    record Absent() implements SubjectAuthenticationOutcome { }

    record Rejected(SubjectAuthenticationRejectReason reason) implements SubjectAuthenticationOutcome {
        public Rejected {
            Require.notNull(reason, DomainException::missingField);
        }
    }
    /** Required authentication could not complete; must never be treated as absent or authenticated. */
    record Failed(SubjectAuthenticationFailReason reason) implements SubjectAuthenticationOutcome {
        public Failed {
            Require.notNull(reason, DomainException::missingField);
        }
    }

    static SubjectAuthenticationOutcome authenticated(AuthenticatedSubject subject) {
        return new Authenticated(subject);
    }

    static SubjectAuthenticationOutcome absent() {
        return new Absent();
    }

    static SubjectAuthenticationOutcome rejected(SubjectAuthenticationRejectReason reason) {
        return new Rejected(reason);
    }

    static SubjectAuthenticationOutcome failed(SubjectAuthenticationFailReason reason) {
        return new Failed(reason);
    }
}
