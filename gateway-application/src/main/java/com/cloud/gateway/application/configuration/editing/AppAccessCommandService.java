package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.application.configuration.definition.WritableAppAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AppAccessCommandService {
    private final WritableAppAccessDefinitionRepository repository;
    private final AccessDefinitionWriter writer;

    public AppAccessCommandService(WritableAppAccessDefinitionRepository repository,
                                   AccessRollbackState rollbackState,
                                   AccessPublishLock publishLock) {
        this.repository = repository;
        this.writer = new AccessDefinitionWriter(rollbackState, publishLock);
    }

    /** Content-only upsert: creation initializes status to ENABLED, updates preserve the existing status. */
    public Revision save(SaveAppAccessCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            AppStatus status = current.find(command.appId()).map(App::status).orElse( AppStatus.ENABLED);
            AppAccess access = new AppAccess(command.allowedOrigins());
            App next = new App(command.appId(), command.platform(), status, access);
            return withApp(current, next);
        });
    }

    /** Dedicated enable/disable transition; the only command that changes an App's status. */
    public Revision setAppStatus(SetAppStatusCommand command) {
        return writer.update(repository, command.expectedSourceRevision(), current -> {
            App existing = current.find(command.appId()).orElseThrow(() -> new DomainException(DomainError.APP_NOT_FOUND));
            App next = new App(existing.appId(), existing.platform(), command.status(),
                    existing.access());
            return withApp(current, next);
        });
    }

    private static AppRegistry withApp(AppRegistry current, App next) {
        Map<AppId, App> updated = new LinkedHashMap<>(current.asMap());
        updated.put(next.appId(), next);
        return new AppRegistry(updated.values());
    }
}
