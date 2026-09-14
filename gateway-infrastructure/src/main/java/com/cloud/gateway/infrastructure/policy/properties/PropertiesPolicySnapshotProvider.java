package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.access.definition.AccessDefinitionReader;
import com.cloud.gateway.application.access.publication.AccessSnapshotAssembler;
import com.cloud.gateway.application.access.publication.AccessSnapshotProvider;
import com.cloud.gateway.application.access.publication.VersionedAccessSnapshot;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import java.time.Clock;

public final class PropertiesAccessSnapshotProvider implements com.cloud.gateway.application.access.publication.AccessSnapshotProvider {
    private static final String SOURCE_REVISION_PREFIX = "properties:";
    private final com.cloud.gateway.application.access.publication.VersionedAccessSnapshot current;

    public PropertiesAccessSnapshotProvider(
            com.cloud.gateway.application.access.definition.AccessDefinitionReader definitions,
            com.cloud.gateway.application.access.publication.AccessSnapshotAssembler assembler,
            Clock clock
    ) {
        IngressAccessSnapshot snapshot = assembler.assemble(0, definitions.loadContent(), clock.instant());
        current = new com.cloud.gateway.application.access.publication.VersionedAccessSnapshot(snapshot, SOURCE_REVISION_PREFIX + snapshot.contentHash());
    }

    @Override
    public com.cloud.gateway.application.access.publication.VersionedAccessSnapshot current() {
        return current;
    }
}
