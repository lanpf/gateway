package com.cloud.gateway.domain.app;

import com.cloud.gateway.domain.common.AggregateRootRegistry;

import java.util.Collection;

public final class AppRegistry extends AggregateRootRegistry<App, AppId> {

    public AppRegistry(Collection<App> apps) {
        super(apps);
    }

}
