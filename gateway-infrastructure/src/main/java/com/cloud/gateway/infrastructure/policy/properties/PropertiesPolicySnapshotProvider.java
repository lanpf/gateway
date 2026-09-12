package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.PolicyDefinitionReader;
import com.cloud.gateway.application.policy.PolicySnapshotAssembler;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.policy.VersionedPolicySnapshot;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import java.time.Clock;

public final class PropertiesPolicySnapshotProvider implements PolicySnapshotProvider {
    private static final String SOURCE_REVISION_PREFIX = "properties:";
    private final VersionedPolicySnapshot current;

    public PropertiesPolicySnapshotProvider(
            PolicyDefinitionReader definitions,
            PolicySnapshotAssembler assembler,
            Clock clock
    ) {
        IngressAccessSnapshot snapshot = assembler.assemble(0, definitions.loadContent(), clock.instant());
        current = new VersionedPolicySnapshot(snapshot, SOURCE_REVISION_PREFIX + snapshot.contentHash());
    }

    @Override
    public VersionedPolicySnapshot current() {
        return current;
    }
}
