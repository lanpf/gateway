package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.access.app.VerifiedApp;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.common.DomainException;

public record IngressAccessGrantedContext(
        VerifiedApp app,
        VerifiedClient client,
        AuthenticatedSubject subject
) {
    public IngressAccessGrantedContext {
        Require.notNull(app, DomainException::missingField);
    }
}
