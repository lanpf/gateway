package com.cloud.gateway.domain.ingress.access;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

import com.cloud.gateway.domain.app.AppId;

public record VerifiedApp(AppId appId, String platform) {
    public VerifiedApp {
        Require.notNull(appId, DomainException::missingField);
        platform = Require.notBlank(platform, DomainException::invalidObjectField).trim();
    }
}
