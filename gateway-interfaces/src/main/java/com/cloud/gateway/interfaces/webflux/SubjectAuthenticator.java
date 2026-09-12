package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import reactor.core.publisher.Mono;

public interface SubjectAuthenticator<T> {
    Mono<SubjectAuthenticationOutcome> authenticate(T credential);
}
