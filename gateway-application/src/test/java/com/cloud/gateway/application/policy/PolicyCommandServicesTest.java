package com.cloud.gateway.application.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.framework.lock.LockContext;
import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.ClientAppStatus;
import com.cloud.gateway.domain.policy.CredentialForwardingPolicy;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.SubjectRequirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

class PolicyCommandServicesTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-28T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldUpdateOnlyRequestedDefinition() {
        TestStore store = new TestStore(snapshot(1, true), content(true));
        store.endpoints.failOnLoad = true;
        IngressClientAppPolicy policy = new IngressClientAppPolicy(
                new ClientAppId("new-app"), ClientAppStatus.ENABLED, "IOS", Set.of());

        clientAppService(store).save(new SaveClientAppPolicyCommand(store.clients.revision(), policy));

        assertEquals(policy, store.clients.value().get(policy.clientAppId()));
        assertEquals(0, store.endpoints.readCount);
        assertEquals(1, store.clients.readCount);
        assertEquals(0, store.channelAccess.readCount);
        assertEquals(0, store.channelCredentials.readCount);
        assertEquals(0, store.routes.readCount);
    }

    @Test
    void shouldPublishDefinitionsAsOneIncrementedSnapshot() {
        TestStore store = new TestStore(snapshot(1, true), content(false));

        PolicyPublicationOutput output = snapshotService(store, version -> Optional.empty())
                .publish(new PublishPolicySnapshotCommand());

        assertEquals(2, output.snapshotVersion());
        assertEquals("disabled", output.contentHash());
        assertFalse(store.current().snapshot().content().routeAccessPolicies()
                .get(new EndpointId("LOGIN")).enabled());
    }

    @Test
    void shouldRejectPublishWhenDefinitionsHaveNoChanges() {
        TestStore store = new TestStore(snapshot(1, true), content(true));

        assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.empty())
                .publish(new PublishPolicySnapshotCommand()));
        assertEquals(0, store.publishCount);
    }

    @Test
    void shouldRejectMixedSnapshotWhenOneDefinitionChangesDuringPublish() {
        TestStore store = new TestStore(snapshot(1, true), content(false));
        store.routes.changeRevisionOnSecondRead = true;

        assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.empty())
                .publish(new PublishPolicySnapshotCommand()));
        assertEquals(0, store.publishCount);
    }

    @Test
    void shouldKeepRollbackStateWhenRollbackFailsAndAllowDifferentTargetOnRetry() {
        TestStore store = new TestStore(snapshot(2, false), content(false));
        PolicySnapshotHistory history = version -> version == 1
                ? Optional.empty() : Optional.of(snapshot(version, true));
        PolicySnapshotCommandService service = snapshotService(store, history);

        assertThrows(ApplicationException.class,
                () -> service.rollback(new RollbackPolicySnapshotCommand(1)));
        assertTrue(store.active);

        PolicyPublicationOutput output = service.rollback(new RollbackPolicySnapshotCommand(7));

        assertEquals(3, output.snapshotVersion());
        assertFalse(store.active);
        assertEquals(List.of("rollback", "publish", "rollback", "publish"), store.lockExecutor.scenes);
    }

    @Test
    void shouldResumeRollbackAfterSnapshotWasPublishedButStateClearFailed() {
        TestStore store = new TestStore(snapshot(2, false), content(false));
        store.failClear = true;
        PolicySnapshotCommandService service = snapshotService(store, version -> Optional.of(snapshot(1, true)));

        assertThrows(RuntimeException.class,
                () -> service.rollback(new RollbackPolicySnapshotCommand(1)));
        assertEquals(1, store.publishCount);
        assertTrue(store.active);

        store.failClear = false;
        PolicyPublicationOutput output = service.rollback(new RollbackPolicySnapshotCommand(1));

        assertEquals(3, output.snapshotVersion());
        assertEquals(1, store.publishCount);
        assertFalse(store.active);
    }

    @Test
    void shouldResumeRollbackWithoutRewritingAlreadyRestoredDefinitions() {
        TestStore store = new TestStore(snapshot(2, false), content(false));
        IngressPolicyContent targetContent = content(true, Map.of(new ClientAppId("restored-app"),
                new IngressClientAppPolicy(
                        new ClientAppId("restored-app"), ClientAppStatus.ENABLED, "WEB", Set.of())));
        IngressPolicySnapshot target = new IngressPolicySnapshot(1, targetContent, "enabled",
                Instant.parse("2026-08-24T00:00:01Z"));
        store.routes.rejectNextWrite = true;
        PolicySnapshotCommandService service = snapshotService(store, version -> Optional.of(target));

        assertThrows(ApplicationException.class,
                () -> service.rollback(new RollbackPolicySnapshotCommand(1)));
        assertEquals(1, store.clients.writeCount);
        assertTrue(store.active);

        service.rollback(new RollbackPolicySnapshotCommand(1));

        assertEquals(1, store.clients.writeCount);
        assertFalse(store.active);
    }

    @Test
    void shouldBlockOrdinaryPublishWhileRollbackStateExists() {
        TestStore store = new TestStore(snapshot(1, true), content(false));
        store.active = true;

        assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.empty())
                .publish(new PublishPolicySnapshotCommand()));
    }

    @Test
    void shouldMapUnavailablePublishLockToPublishLockError() {
        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> new PolicyPublishLock(new UnavailableLockExecutor()).execute(() -> Boolean.TRUE));

        assertEquals(ApplicationError.APP_POLICY_PUBLISH_LOCK_UNAVAILABLE.errorCode(), exception.getErrorCode());
    }

    @Test
    void shouldMapUnavailableRollbackLockToRollbackLockError() {
        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> new PolicyRollbackLock(new UnavailableLockExecutor()).execute(() -> Boolean.TRUE));

        assertEquals(ApplicationError.APP_POLICY_ROLLBACK_LOCK_UNAVAILABLE.errorCode(), exception.getErrorCode());
    }

    private static ClientAppPolicyCommandService clientAppService(TestStore store) {
        return new ClientAppPolicyCommandService(store.clients, store, new PolicyPublishLock(store.lockExecutor));
    }

    private static PolicySnapshotCommandService snapshotService(TestStore store, PolicySnapshotHistory history) {
        return new PolicySnapshotCommandService(store.endpoints, store.clients, store.channelAccess,
                store.channelCredentials, store.routes, store, store, history, store,
                content -> content.routeAccessPolicies().get(new EndpointId("LOGIN")).enabled()
                        ? "enabled" : "disabled",
                new PolicyPublishLock(store.lockExecutor), new PolicyRollbackLock(store.lockExecutor), CLOCK);
    }

    private static IngressPolicySnapshot snapshot(long version, boolean enabled) {
        return new IngressPolicySnapshot(version, content(enabled), enabled ? "enabled" : "disabled",
                Instant.parse("2026-08-24T00:00:00Z").plusSeconds(version));
    }

    private static IngressPolicyContent content(boolean enabled) {
        return content(enabled, Map.of());
    }

    private static IngressPolicyContent content(
            boolean enabled,
            Map<ClientAppId, IngressClientAppPolicy> clientApps
    ) {
        EndpointCatalog catalog = new EndpointCatalog(List.of(new EndpointDefinition(new EndpointId("LOGIN"),
                "service", List.of(new EndpointMatcher("POST", "/login")), "service")));
        return new IngressPolicyContent(catalog, clientApps, Map.of(), Map.of(),
                Map.of(new EndpointId("LOGIN"), route(enabled)));
    }

    private static IngressRouteAccessPolicy route(boolean enabled) {
        return new IngressRouteAccessPolicy(new EndpointId("LOGIN"), ChannelCredentialRequirement.IGNORE,
                ChannelRequirement.FORBIDDEN, SubjectRequirement.IGNORE, Set.of(), Set.of(),
                CredentialForwardingPolicy.stripAll(), enabled);
    }

    private static class TestDefinition<T> {
        private T value;
        private int revision;
        int readCount;
        int writeCount;
        boolean changeRevisionOnSecondRead;
        boolean failOnLoad;
        boolean rejectNextWrite;

        TestDefinition(T value) { this.value = value; }

        public VersionedPolicyDefinition<T> load() {
            readCount++;
            if (failOnLoad) {
                throw new AssertionError("unrelated definition must not be loaded");
            }
            if (changeRevisionOnSecondRead && readCount == 2) {
                revision++;
            }
            return new VersionedPolicyDefinition<>(value, revision());
        }

        public boolean compareAndSet(String expectedSourceRevision, T next) {
            if (rejectNextWrite) {
                rejectNextWrite = false;
                return false;
            }
            if (!revision().equals(expectedSourceRevision)) {
                return false;
            }
            value = next;
            revision++;
            writeCount++;
            return true;
        }

        String revision() { return "definition-" + revision; }
        T value() { return value; }
    }

    private static final class EndpointDefinitions extends TestDefinition<EndpointCatalog>
            implements WritableEndpointCatalogDefinitionRepository {
        EndpointDefinitions(EndpointCatalog value) { super(value); }
    }

    private static final class ClientDefinitions extends TestDefinition<Map<ClientAppId, IngressClientAppPolicy>>
            implements WritableClientAppPolicyDefinitionRepository {
        ClientDefinitions(Map<ClientAppId, IngressClientAppPolicy> value) { super(new LinkedHashMap<>(value)); }
    }

    private static final class ChannelAccessDefinitions
            extends TestDefinition<Map<ChannelCode, IngressChannelAccessPolicy>>
            implements WritableChannelAccessPolicyDefinitionRepository {
        ChannelAccessDefinitions(Map<ChannelCode, IngressChannelAccessPolicy> value) {
            super(new LinkedHashMap<>(value));
        }
    }

    private static final class ChannelCredentialDefinitions
            extends TestDefinition<Map<ChannelCode, IngressChannelCredentialPolicy>>
            implements WritableChannelCredentialPolicyDefinitionRepository {
        ChannelCredentialDefinitions(Map<ChannelCode, IngressChannelCredentialPolicy> value) {
            super(new LinkedHashMap<>(value));
        }
    }

    private static final class RouteDefinitions extends TestDefinition<Map<EndpointId, IngressRouteAccessPolicy>>
            implements WritableRouteAccessPolicyDefinitionRepository {
        RouteDefinitions(Map<EndpointId, IngressRouteAccessPolicy> value) { super(new LinkedHashMap<>(value)); }
    }

    private static final class TestStore implements PolicySnapshotProvider, PolicySnapshotPublisher,
            PolicyRollbackState {
        private VersionedPolicySnapshot snapshot;
        private final EndpointDefinitions endpoints;
        private final ClientDefinitions clients;
        private final ChannelAccessDefinitions channelAccess;
        private final ChannelCredentialDefinitions channelCredentials;
        private final RouteDefinitions routes;
        private int publishCount;
        private boolean active;
        private boolean failClear;
        private final RecordingLockExecutor lockExecutor = new RecordingLockExecutor();

        private TestStore(IngressPolicySnapshot snapshot, IngressPolicyContent definitions) {
            this.snapshot = new VersionedPolicySnapshot(snapshot, "snapshot-1");
            endpoints = new EndpointDefinitions(definitions.endpointCatalog());
            clients = new ClientDefinitions(definitions.clientAppPolicies());
            channelAccess = new ChannelAccessDefinitions(definitions.channelAccessPolicies());
            channelCredentials = new ChannelCredentialDefinitions(definitions.channelCredentialPolicies());
            routes = new RouteDefinitions(definitions.routeAccessPolicies());
        }

        @Override public VersionedPolicySnapshot current() { return snapshot; }

        @Override public boolean compareAndSet(String expectedSourceRevision, IngressPolicySnapshot next) {
            if (!snapshot.sourceRevision().equals(expectedSourceRevision)) {
                return false;
            }
            snapshot = new VersionedPolicySnapshot(next, "snapshot-" + next.snapshotVersion());
            publishCount++;
            return true;
        }

        @Override public boolean isActive() { return active; }
        @Override public void activate() { active = true; }
        @Override public void clear() {
            if (failClear) {
                throw new RuntimeException("clear failed");
            }
            active = false;
        }
    }

    private static final class RecordingLockExecutor implements LockExecutor {
        private final List<String> scenes = new ArrayList<>();

        @Override public <T> Optional<T> execute(LockContext context, Callable<T> callable) throws Exception {
            scenes.add(context.lockNames()[0]);
            return Optional.ofNullable(callable.call());
        }
    }

    private static final class UnavailableLockExecutor implements LockExecutor {
        @Override public <T> Optional<T> execute(LockContext context, Callable<T> callable) {
            return Optional.empty();
        }
    }
}
