package com.cloud.gateway.application.access.definition;

import com.cloud.gateway.domain.app.AppRegistry;

public interface WritableAppAccessDefinitionRepository
        extends AppAccessDefinitionRepository,
        WritableAccessDefinitionRepository<AppRegistry> {
}
