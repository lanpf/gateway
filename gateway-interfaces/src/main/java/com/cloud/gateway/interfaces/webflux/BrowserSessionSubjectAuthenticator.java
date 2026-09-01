package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.domain.access.SubjectAuthenticationResult;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public final class BrowserSessionSubjectAuthenticator implements SubjectAuthenticator<String> {
    private final BrowserSessionVerifier verifier;

    @Override
    public Mono<SubjectAuthenticationResult> authenticate(String cookieValue) {
        return Mono.fromCompletionStage(verifier.verify(cookieValue))
                .map(subject -> subject.<SubjectAuthenticationResult>map(SubjectAuthenticationResult::authenticated)
                        .orElseGet(SubjectAuthenticationResult::rejected));
    }
}
