package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.util.Set;

public record IngressChannelAccessPolicy(ChannelCode channelCode, Set<ClientAppId> allowedClientAppIds) {
    public IngressChannelAccessPolicy {
        Require.notNull(channelCode, DomainException::missingField);
        allowedClientAppIds = Set.copyOf(Require.notNull(allowedClientAppIds, DomainException::missingField));
        allowedClientAppIds.forEach(value -> Require.notNull(value, DomainException::missingField));
    }

    public boolean allows(ClientAppId clientAppId) { return allowedClientAppIds.contains(clientAppId); }
}
