package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.configuration.definition.AccessDefinitionReader;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotAssembler;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.application.configuration.publication.RevisionedAccessSnapshot;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import java.time.Clock;

public final class PropertiesAccessSnapshotCapturer implements AccessSnapshotCapturer {
    private static final String SOURCE_REVISION_PREFIX = "properties:";
    /** A read-only local source has no publication history, so it exposes a synthetic first version. */
    private static final SnapshotVersion INITIAL_SNAPSHOT_VERSION = new SnapshotVersion(1);
    private final RevisionedAccessSnapshot current;

    public PropertiesAccessSnapshotCapturer(
            AccessDefinitionReader definitions,
            AccessSnapshotAssembler assembler,
            Clock clock
    ) {
        IngressSnapshot snapshot = assembler.assemble(INITIAL_SNAPSHOT_VERSION, definitions.loadContent(),
                clock.instant());
        current = new RevisionedAccessSnapshot(snapshot,
                new Revision(SOURCE_REVISION_PREFIX + snapshot.contentHash()));
    }

    @Override
    public RevisionedAccessSnapshot capture() {
        return current;
    }
}
