package com.cloud.gateway.application.security;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.ingress.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationFailReason;
import java.util.Objects;

/** Browser Session 在线验证结局；区分凭据无效与验证依赖不可用。 */
public sealed interface BrowserSessionVerificationOutcome {
    record Verified(AuthenticatedSubject subject) implements BrowserSessionVerificationOutcome {
        public Verified {
            Require.notNull(subject);
        }
    }

    record Invalid() implements BrowserSessionVerificationOutcome {
    }

    record Unavailable(SubjectAuthenticationFailReason reason) implements BrowserSessionVerificationOutcome {
        public Unavailable {
            Require.notNull(reason);
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
