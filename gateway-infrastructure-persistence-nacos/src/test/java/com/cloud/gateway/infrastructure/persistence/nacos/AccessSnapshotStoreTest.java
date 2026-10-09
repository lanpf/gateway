package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.nacos.api.exception.NacosException;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.locks.LockSupport;

import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodAccessSnapshotStore;
import com.cloud.gateway.infrastructure.policy.lkg.LastKnownGoodAccessSnapshotStore;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccessSnapshotStoreTest {
    @TempDir Path temporaryDirectory;
    private final List<NacosAccessSnapshotStore> stores = new CopyOnWriteArrayList<>();
    private final StoredAccessSnapshotCodec codec = PolicyTestFixtures.codec();

    @AfterEach
    void closeStores() {
        stores.forEach(NacosAccessSnapshotStore::close);
    }

    @Test
    void shouldPublishWithCasAndPropagateToEveryStore() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(
                codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true)));
        NacosAccessSnapshotStore first = store(operations, "first.yaml");
        NacosAccessSnapshotStore second = store(operations, "second.yaml");
        first.initialize();
        second.initialize();
        com.cloud.gateway.domain.ingress.snapshot.Revision expectedRevision = first.capture().sourceRevision();

        boolean published = first.compareAndSet(
                expectedRevision, PolicyTestFixtures.snapshot(2, false));

        assertEquals(true, published);
        await(() -> second.capture().snapshot().version().value() == 2);
        assertEquals(false, second.capture().snapshot().content().endpoints().values()
                .iterator().next().enabled());
        assertEquals(2, operations.readCount);
    }

    @Test
    void shouldRejectStaleSourceRevisionWithoutOverwriting() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(
                codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true)));
        NacosAccessSnapshotStore store = store(operations, "cas.yaml");
        store.initialize();

        assertFalse(store.compareAndSet(new com.cloud.gateway.domain.ingress.snapshot.Revision("stale"),
                PolicyTestFixtures.snapshot(2, false)));
        assertEquals(1, store.capture().snapshot().version().value());
    }

    @Test
    void shouldStartFromLastKnownGoodWhenNacosIsUnavailable() {
        NacosPolicyProperties properties = properties("fallback.yaml");
        LastKnownGoodAccessSnapshotStore lastKnownGood = new FileLastKnownGoodAccessSnapshotStore(
                properties.getLastKnownGoodLocation());
        lastKnownGood.save(codec.encodeSnapshot(PolicyTestFixtures.snapshot(4, true)));
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(null);
        operations.unavailable = true;
        NacosAccessSnapshotStore store = new NacosAccessSnapshotStore(
                operations, properties, codec, lastKnownGood);
        stores.add(store);

        store.initialize();

        assertEquals(4, store.capture().snapshot().version().value());
    }

    @Test
    void shouldUpdateOnlySourceRevisionForRepresentationOnlyChange() {
        String initial = codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true));
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(initial);
        NacosAccessSnapshotStore store = store(operations, "format.yaml");
        store.initialize();
        IngressSnapshot original = store.capture().snapshot();

        operations.replace(initial + System.lineSeparator() + "# formatting-only" + System.lineSeparator());

        await(() -> !store.capture().sourceRevision().value().equals(NacosAccessSnapshotStore.sourceRevision(initial)));
        assertSame(original, store.capture().snapshot());
    }

    @Test
    void shouldKeepCurrentSnapshotWhenNotifiedContentMovesBackward() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(
                codec.encodeSnapshot(PolicyTestFixtures.snapshot(2, false)));
        NacosAccessSnapshotStore store = store(operations, "backward.yaml");
        store.initialize();

        operations.replace(codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true)));

        String currentRepresentation = codec.encodeSnapshot(PolicyTestFixtures.snapshot(2, false))
                + System.lineSeparator() + "# current representation" + System.lineSeparator();
        operations.replace(currentRepresentation);

        await(() -> store.capture().sourceRevision().value()
                .equals(NacosAccessSnapshotStore.sourceRevision(currentRepresentation)));
        assertEquals(2, store.capture().snapshot().version().value());
        assertEquals(1, operations.readCount);
    }

    @Test
    void shouldFailFirstStartWithoutRemoteOrLastKnownGoodSnapshot() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(null);
        operations.unavailable = true;
        NacosAccessSnapshotStore store = store(operations, "missing.yaml");

        assertThrows(InfrastructureException.class, store::initialize);
    }

    @Test
    void shouldKeepAcceptedSnapshotWhenSameVersionHasConflictingContent() {
        String initial = codec.encodeSnapshot(PolicyTestFixtures.snapshot(2, true));
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(initial);
        NacosAccessSnapshotStore store = store(operations, "conflict.yaml");
        store.initialize();
        IngressSnapshot accepted = store.capture().snapshot();
        operations.replace(codec.encodeSnapshot(PolicyTestFixtures.snapshot(2, false)));
        // Notifications use one executor; the marker ensures the conflicting notification was processed.
        String marker = initial + System.lineSeparator() + "# after conflict" + System.lineSeparator();
        operations.replace(marker);
        await(() -> store.capture().sourceRevision().value().equals(NacosAccessSnapshotStore.sourceRevision(marker)));
        assertSame(accepted, store.capture().snapshot());
    }

    @Test
    void shouldCountRepeatedInvalidNotificationsAndRecoverOnAValidOne() {
        String initial = codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true));
        var operations = new FakeNacosConfigOperations(initial);
        var store = store(operations, "metrics.yaml");
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        store.bindMetrics(registry);
        store.initialize();
        operations.replace("invalid: [");
        operations.replace("invalid: [");
        operations.replace("invalid: [");
        await(() -> registry.get("gateway.access.refresh.failures").functionCounter().count() == 3);
        assertEquals(1, registry.get("gateway.access.refresh.degraded").gauge().value());
        String recovered = initial + "\n# recovered\n";
        operations.replace(recovered);
        await(() -> registry.get("gateway.access.refresh.degraded").gauge().value() == 0);
        assertEquals(3, registry.get("gateway.access.refresh.failures").functionCounter().count());
        org.junit.jupiter.api.Assertions.assertTrue(
                registry.get("gateway.access.refresh.last_success_seconds").gauge().value() > 0);
    }

    @Test
    void shouldRetryFailedLkgPersistenceForTheSameAcceptedSnapshot() {
        String initial = codec.encodeSnapshot(PolicyTestFixtures.snapshot(2, true));
        var operations = new FakeNacosConfigOperations(initial);
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        LastKnownGoodAccessSnapshotStore lkg = new LastKnownGoodAccessSnapshotStore() {
            public java.util.Optional<String> load() { return java.util.Optional.empty(); }
            public void save(String value) {
                if (attempts.incrementAndGet() == 1) {
                    throw new InfrastructureException(com.cloud.gateway.infrastructure.common.InfrastructureError.INFRA_TECH_POLICY_LAST_KNOWN_GOOD_FAILED);
                }
            }
        };
        var store = new NacosAccessSnapshotStore(operations, properties("lkg-retry.yaml"), codec, lkg);
        stores.add(store);
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        store.bindMetrics(registry);
        store.initialize();
        assertEquals(1, attempts.get());
        operations.replace(codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true)));
        await(() -> registry.get("gateway.access.stale_notifications").functionCounter().count() == 1);
        assertEquals(1, attempts.get(), "A stale version with the same content must not replace LKG");
        operations.replace(initial);
        await(() -> attempts.get() == 2);
        assertEquals(2, store.capture().snapshot().version().value());
    }

    private NacosAccessSnapshotStore store(FakeNacosConfigOperations operations, String filename) {
        NacosPolicyProperties properties = properties(filename);
        NacosAccessSnapshotStore store = new NacosAccessSnapshotStore(operations, properties, codec,
                new FileLastKnownGoodAccessSnapshotStore(properties.getLastKnownGoodLocation()));
        stores.add(store);
        return store;
    }

    private NacosPolicyProperties properties(String filename) {
        NacosPolicyProperties properties = new NacosPolicyProperties();
        properties.setLastKnownGoodLocation(temporaryDirectory.resolve(filename));
        properties.setRecoveryInterval(Duration.ofHours(1));
        return properties;
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for asynchronous access refresh");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    private static final class FakeNacosConfigOperations implements NacosConfigOperations {
        private final AtomicReference<String> content;
        private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
        private volatile boolean unavailable;
        private volatile int readCount;

        private FakeNacosConfigOperations(String initialContent) {
            this.content = new AtomicReference<>(initialContent);
        }

        @Override
        public String getConfig(String dataId, String group, long timeoutMillis) throws NacosException {
            readCount++;
            ensureAvailable();
            return content.get();
        }

        @Override
        public String getConfigAndListen(
                String dataId, String group, long timeoutMillis, Consumer<String> changeCallback
        ) throws NacosException {
            ensureAvailable();
            listeners.add(changeCallback);
            readCount++;
            return content.get();
        }

        @Override
        public boolean publishConfigCas(
                String dataId, String group, String next, String expectedMd5
        ) throws NacosException {
            ensureAvailable();
            String observed = content.get();
            if (observed == null || !NacosAccessSnapshotStore.sourceRevision(observed).equals(expectedMd5)) {
                return false;
            }
            if (!content.compareAndSet(observed, next)) {
                return false;
            }
            listeners.forEach(listener -> listener.accept(next));
            return true;
        }

        @Override
        public void removeListener(String dataId, String group, Consumer<String> changeCallback) {
            listeners.remove(changeCallback);
        }

        private void replace(String next) {
            content.set(next);
            listeners.forEach(listener -> listener.accept(next));
        }

        private void ensureAvailable() throws NacosException {
            if (unavailable) {
                throw new NacosException(NacosException.SERVER_ERROR, "unavailable");
            }
        }
    }
}
