package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodPolicySnapshotStore;
import com.cloud.gateway.infrastructure.policy.lkg.LastKnownGoodPolicySnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LastKnownGoodPolicySnapshotStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void shouldPersistAndReplaceLastKnownGoodContent() {
        LastKnownGoodPolicySnapshotStore store = new FileLastKnownGoodPolicySnapshotStore(
                temporaryDirectory.resolve("policy.yaml"));

        assertTrue(store.load().isEmpty());
        store.save("first");
        store.save("second");

        assertEquals("second", store.load().orElseThrow());
    }
}
