package com.cloud.gateway.infrastructure.coordination.redis;

import com.cloud.framework.core.naming.ResourceNameResolver;
import com.cloud.framework.starter.autoconfigure.naming.AbstractKeyResolver;

public final class AccessRollbackStateKeyResolver extends AbstractKeyResolver {
    public AccessRollbackStateKeyResolver(ResourceNameResolver resourceNameResolver) {
        super(resourceNameResolver);
    }

    @Override
    protected String[] prefixes() {
        return new String[]{"policy"};
    }

    String rollbackState() {
        return resolve("rollback-state");
    }
}
