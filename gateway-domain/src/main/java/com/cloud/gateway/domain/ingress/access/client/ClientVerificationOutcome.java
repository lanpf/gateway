package com.cloud.gateway.domain.ingress.access.client;


import com.cloud.framework.core.validation.Require;

public sealed interface ClientVerificationOutcome {

    record Verified(VerifiedClient client) implements ClientVerificationOutcome {
        public Verified {
            Require.notNull(client);
        }
    }

    record Absent() implements ClientVerificationOutcome { }

    record Rejected() implements ClientVerificationOutcome { }

    /** Required verification could not complete; must never be treated as absent or verified. */
    record Failed() implements ClientVerificationOutcome { }

    static ClientVerificationOutcome verified(VerifiedClient client) {
        return new Verified(client);
    }

    static ClientVerificationOutcome absent() {
        return new Absent();
    }

    static ClientVerificationOutcome rejected() {
        return new Rejected();
    }

    static ClientVerificationOutcome failed() {
        return new Failed();
    }
}
