package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodAccessSnapshotStore;
import com.cloud.gateway.infrastructure.policy.lkg.LastKnownGoodAccessSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LastKnownGoodAccessSnapshotStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void shouldPersistAndReplaceLastKnownGoodContent() {
        LastKnownGoodAccessSnapshotStore store = new FileLastKnownGoodAccessSnapshotStore(
                temporaryDirectory.resolve("access.yaml"));

        assertTrue(store.load().isEmpty());
        store.save("first");
        store.save("second");

        assertEquals("second", store.load().orElseThrow());
    }
}
