package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AppAccessCommandService {
    private final com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository repository;
    private final AccessDefinitionWriter writer;

    public AppAccessCommandService(com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository repository,
            com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState, com.cloud.gateway.application.access.publication.AccessPublishLock publishLock) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
    }

    /** Content-only upsert: creation initializes status to ENABLED, updates preserve the existing status. */
    public String save(SaveAppAccessCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            App existing = current.find(command.appId()).orElse(null);
            App next = existing == null
                    ? new App(command.appId(), command.platform(), AppStatus.ENABLED,
                            new AppAccess(command.allowedOrigins()))
                    : new App(command.appId(), command.platform(), existing.status(),
                            new AppAccess(command.allowedOrigins()));
            return withApp(current, next);
        });
    }

    /** Dedicated enable/disable transition; the only command that changes an App's status. */
    public String setAppStatus(SetAppStatusCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            App existing = Require.that(current.find(command.appId()).orElse(null), Objects::nonNull,
                    () -> new DomainException(DomainError.APP_NOT_FOUND));
            return withApp(current, new App(existing.appId(), existing.platform(), command.status(),
                    existing.access()));
        });
    }

    private static AppRegistry withApp(AppRegistry current, App next) {
        Map<AppId, App> updated = new LinkedHashMap<>(current.asMap());
        updated.put(next.appId(), next);
        return new AppRegistry(updated.values());
    }
}
