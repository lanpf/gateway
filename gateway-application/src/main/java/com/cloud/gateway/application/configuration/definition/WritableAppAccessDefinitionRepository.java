package com.cloud.gateway.application.configuration.definition;

import com.cloud.gateway.domain.app.AppRegistry;

public interface WritableAppAccessDefinitionRepository
        extends AppAccessDefinitionRepository,
        WritableAccessDefinitionRepository<AppRegistry> {
}
