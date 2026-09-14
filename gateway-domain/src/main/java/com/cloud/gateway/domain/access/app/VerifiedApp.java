package com.cloud.gateway.domain.access.app;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.common.DomainException;

public record VerifiedApp(AppId appId, String platform) {
    public VerifiedApp {
        Require.notNull(appId, DomainException::missingField);
        platform = Require.notBlank(platform, DomainException::missingField).trim();
    }
}
