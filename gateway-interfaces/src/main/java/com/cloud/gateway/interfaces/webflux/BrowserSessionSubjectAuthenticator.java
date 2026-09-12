package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.application.security.BrowserSessionVerificationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public final class BrowserSessionSubjectAuthenticator implements SubjectAuthenticator<String> {
    private final BrowserSessionVerifier verifier;

    @Override
    public Mono<SubjectAuthenticationOutcome> authenticate(String cookieValue) {
        return Mono.fromCompletionStage(verifier.verify(cookieValue))
                .map(BrowserSessionSubjectAuthenticator::toAuthenticationOutcome);
    }

    private static SubjectAuthenticationOutcome toAuthenticationOutcome(
            BrowserSessionVerificationOutcome verification) {
        if (verification instanceof BrowserSessionVerificationOutcome.Verified verified) {
            return SubjectAuthenticationOutcome.authenticated(verified.subject());
        }
        if (verification instanceof BrowserSessionVerificationOutcome.Unavailable unavailable) {
            return SubjectAuthenticationOutcome.failed(unavailable.reason());
        }
        return SubjectAuthenticationOutcome.rejected();
    }
}
