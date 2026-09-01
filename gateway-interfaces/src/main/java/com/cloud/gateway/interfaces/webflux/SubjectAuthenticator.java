package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.domain.access.SubjectAuthenticationResult;
import reactor.core.publisher.Mono;

public interface SubjectAuthenticator<T> {
    Mono<SubjectAuthenticationResult> authenticate(T credential);
}
