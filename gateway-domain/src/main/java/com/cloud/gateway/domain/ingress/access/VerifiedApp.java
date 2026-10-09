package com.cloud.gateway.domain.ingress.access;


import com.cloud.framework.core.validation.Require;

import com.cloud.gateway.domain.app.AppId;

public record VerifiedApp(AppId appId, String platform) {
    public VerifiedApp {
        Require.notNull(appId);
        platform = Require.notBlank(platform).trim();
    }
}
