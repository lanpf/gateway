package com.cloud.gateway.infrastructure.replay.redis;

import com.cloud.framework.core.naming.ResourceNameResolver;
import com.cloud.framework.starter.autoconfigure.naming.AbstractKeyResolver;

public final class ReplayGuardKeyResolver extends AbstractKeyResolver {

    public ReplayGuardKeyResolver(ResourceNameResolver resourceNameResolver) {
        super(resourceNameResolver);
    }

    @Override
    protected String[] prefixes() {
        return new String[]{"replay"};
    }

    String evidence(String evidenceKey) {
        return resolve(evidenceKey);
    }
}
