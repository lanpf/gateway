package com.cloud.gateway.application.security;

import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationFailReason;
import java.util.Objects;

/** Browser Session 在线验证结局；区分凭据无效与验证依赖不可用。 */
public sealed interface BrowserSessionVerificationOutcome {
    record Verified(AuthenticatedSubject subject) implements BrowserSessionVerificationOutcome {
        public Verified {
            Objects.requireNonNull(subject, "subject must not be null");
        }
    }

    record Invalid() implements BrowserSessionVerificationOutcome {
    }

    record Unavailable(SubjectAuthenticationFailReason reason) implements BrowserSessionVerificationOutcome {
        public Unavailable {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    static BrowserSessionVerificationOutcome verified(AuthenticatedSubject subject) {
        return new Verified(subject);
    }

    static BrowserSessionVerificationOutcome invalid() {
        return new Invalid();
    }

    static BrowserSessionVerificationOutcome unavailable(SubjectAuthenticationFailReason reason) {
        return new Unavailable(reason);
    }
}
