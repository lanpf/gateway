package com.cloud.gateway.domain.ingress.access;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

import com.cloud.gateway.domain.ingress.access.client.VerifiedClient;
import com.cloud.gateway.domain.ingress.access.subject.AuthenticatedSubject;

public record AccessGrantedContext(
        VerifiedApp app,
        VerifiedClient client,
        AuthenticatedSubject subject
) {
    public AccessGrantedContext {
        Require.notNull(app, DomainException::missingField);
    }
}
