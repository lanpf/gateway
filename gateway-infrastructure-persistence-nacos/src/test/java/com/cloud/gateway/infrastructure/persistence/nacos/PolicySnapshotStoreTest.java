package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.nacos.api.exception.NacosException;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
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

import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodPolicySnapshotStore;
import com.cloud.gateway.infrastructure.policy.lkg.LastKnownGoodPolicySnapshotStore;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicySnapshotStoreTest {
    @TempDir Path temporaryDirectory;
    private final List<NacosPolicySnapshotStore> stores = new CopyOnWriteArrayList<>();
    private final StoredPolicySnapshotCodec codec = PolicyTestFixtures.codec();

    @AfterEach
    void closeStores() {
        stores.forEach(NacosPolicySnapshotStore::close);
    }

    @Test
    void shouldPublishWithCasAndPropagateToEveryStore() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(
                codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true)));
        NacosPolicySnapshotStore first = store(operations, "first.yaml");
        NacosPolicySnapshotStore second = store(operations, "second.yaml");
        first.initialize();
        second.initialize();
        String expectedRevision = first.current().sourceRevision();

        boolean published = first.compareAndSet(
                expectedRevision, PolicyTestFixtures.snapshot(2, false));

        assertEquals(true, published);
        await(() -> second.current().snapshot().snapshotVersion() == 2);
        assertEquals(false, second.current().snapshot().content().routeAccessPolicies().values()
                .iterator().next().enabled());
        assertEquals(2, operations.readCount);
    }

    @Test
    void shouldRejectStaleSourceRevisionWithoutOverwriting() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(
                codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true)));
        NacosPolicySnapshotStore store = store(operations, "cas.yaml");
        store.initialize();

        assertFalse(store.compareAndSet("stale", PolicyTestFixtures.snapshot(2, false)));
        assertEquals(1, store.current().snapshot().snapshotVersion());
    }

    @Test
    void shouldStartFromLastKnownGoodWhenNacosIsUnavailable() {
        NacosPolicyProperties properties = properties("fallback.yaml");
        LastKnownGoodPolicySnapshotStore lastKnownGood = new FileLastKnownGoodPolicySnapshotStore(
                properties.getLastKnownGoodLocation());
        lastKnownGood.save(codec.encodeSnapshot(PolicyTestFixtures.snapshot(4, true)));
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(null);
        operations.unavailable = true;
        NacosPolicySnapshotStore store = new NacosPolicySnapshotStore(
                operations, properties, codec, lastKnownGood);
        stores.add(store);

        store.initialize();

        assertEquals(4, store.current().snapshot().snapshotVersion());
    }

    @Test
    void shouldUpdateOnlySourceRevisionForRepresentationOnlyChange() {
        String initial = codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true));
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(initial);
        NacosPolicySnapshotStore store = store(operations, "format.yaml");
        store.initialize();
        IngressPolicySnapshot original = store.current().snapshot();

        operations.replace(initial + System.lineSeparator() + "# formatting-only" + System.lineSeparator());

        await(() -> !store.current().sourceRevision().equals(NacosPolicySnapshotStore.sourceRevision(initial)));
        assertSame(original, store.current().snapshot());
    }

    @Test
    void shouldKeepCurrentSnapshotWhenNotifiedContentMovesBackward() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(
                codec.encodeSnapshot(PolicyTestFixtures.snapshot(2, false)));
        NacosPolicySnapshotStore store = store(operations, "backward.yaml");
        store.initialize();

        operations.replace(codec.encodeSnapshot(PolicyTestFixtures.snapshot(1, true)));

        String currentRepresentation = codec.encodeSnapshot(PolicyTestFixtures.snapshot(2, false))
                + System.lineSeparator() + "# current representation" + System.lineSeparator();
        operations.replace(currentRepresentation);

        await(() -> store.current().sourceRevision()
                .equals(NacosPolicySnapshotStore.sourceRevision(currentRepresentation)));
        assertEquals(2, store.current().snapshot().snapshotVersion());
        assertEquals(1, operations.readCount);
    }

    @Test
    void shouldFailFirstStartWithoutRemoteOrLastKnownGoodSnapshot() {
        FakeNacosConfigOperations operations = new FakeNacosConfigOperations(null);
        operations.unavailable = true;
        NacosPolicySnapshotStore store = store(operations, "missing.yaml");

        assertThrows(InfrastructureException.class, store::initialize);
    }

    private NacosPolicySnapshotStore store(FakeNacosConfigOperations operations, String filename) {
        NacosPolicyProperties properties = properties(filename);
        NacosPolicySnapshotStore store = new NacosPolicySnapshotStore(operations, properties, codec,
                new FileLastKnownGoodPolicySnapshotStore(properties.getLastKnownGoodLocation()));
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
                throw new AssertionError("Timed out waiting for asynchronous policy refresh");
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
            if (observed == null || !NacosPolicySnapshotStore.sourceRevision(observed).equals(expectedMd5)) {
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
