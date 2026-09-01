package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

public record IngressClientAppPolicy(ClientAppId clientAppId, ClientAppStatus status, String platform) {
    public IngressClientAppPolicy {
        Require.notNull(clientAppId, DomainException::missingField);
        Require.notNull(status, DomainException::missingField);
        platform = Require.notBlank(platform, DomainException::missingField).trim();
    }

    public boolean enabled() { return status == ClientAppStatus.ENABLED; }
}
