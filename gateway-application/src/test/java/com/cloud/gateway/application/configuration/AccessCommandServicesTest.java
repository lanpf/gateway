package com.cloud.gateway.application.configuration;

import com.cloud.gateway.domain.capability.configuration.CapabilityBundle;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.domain.capability.configuration.CapabilityBundleId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.framework.lock.LockContext;
import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.application.configuration.publication.RevisionedAccessSnapshot;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;
import com.cloud.gateway.domain.capability.header.HeaderPolicy;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.app.AppAccess;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.app.AppStatus;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.capability.configuration.*;
import com.cloud.gateway.domain.capability.resilience.TimeoutPolicy;
import java.time.Clock;
import java.time.Duration;
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

class AccessCommandServicesTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-28T01:00:00Z"), ZoneOffset.UTC);
    private static final AppId APP_ID = new AppId("existing-app");
    private static final ClientId CLIENT_ID = new ClientId("client-a");

    @Test
    void shouldAllowPlatformChangeWhenSavingExistingApp() {
        IngressContent original = content(true, Map.of(APP_ID, app("WEB")));
        TestStore store = new TestStore(snapshot(1, original), original);
        appService(store).save(new com.cloud.gateway.application.configuration.editing.SaveAppAccessCommand(store.clients.revision(), APP_ID, "IOS", Set.of()));
        assertEquals("IOS", store.clients.value().find(APP_ID).orElseThrow().platform());
        assertEquals(1, store.clients.writeCount);
        assertEquals("WEB", store.capture().snapshot().content().apps().find(APP_ID)
                .orElseThrow().platform());
        assertEquals(0, store.publishCount);
    }

    @Test
    void shouldPreserveStatusWhenSavingExistingAppContent() {
        IngressContent original = content(true, Map.of(APP_ID, app("WEB")));
        TestStore store = new TestStore(snapshot(1, original), original);
        store.disableApp(APP_ID);
        appService(store).save(new com.cloud.gateway.application.configuration.editing.SaveAppAccessCommand(store.clients.revision(), APP_ID, "IOS", Set.of()));
        assertEquals(AppStatus.DISABLED, store.clients.value().find(APP_ID).orElseThrow().status());
        assertEquals("IOS", store.clients.value().find(APP_ID).orElseThrow().platform());
    }

    @Test
    void shouldCreateAppEnabledWhenSavingNewApp() {
        TestStore store = new TestStore(snapshot(1, content(true)), content(true));
        appService(store).save(new com.cloud.gateway.application.configuration.editing.SaveAppAccessCommand(store.clients.revision(), APP_ID, "IOS", Set.of()));
        assertEquals(AppStatus.ENABLED, store.clients.value().find(APP_ID).orElseThrow().status());
    }

    @Test
    void shouldPublishPlatformChangeFromDirectDefinitionEdit() {
        IngressContent original = content(true, Map.of(APP_ID, app("WEB")));
        IngressContent edited = content(true, Map.of(APP_ID, app("IOS")));
        TestStore store = new TestStore(snapshot(1, original), edited);
        snapshotService(store, com.cloud.gateway.application.configuration.publication.AccessSnapshotHistory.empty()).publish(new com.cloud.gateway.application.configuration.publication.PublishAccessSnapshotCommand());
        assertEquals(1, store.publishCount);
        assertEquals(2, store.capture().snapshot().version().value());
        assertEquals("IOS", store.capture().snapshot().content().apps().find(APP_ID).orElseThrow().platform());
        assertEquals("WEB", original.apps().find(APP_ID).orElseThrow().platform());
    }

    @Test
    void shouldRollbackPlatformChangesAgainstRunningAndEditedApps() {
        IngressContent web = content(true, Map.of(APP_ID, app("WEB")));
        IngressContent ios = content(false, Map.of(APP_ID, app("IOS")));
        IngressSnapshot target = new IngressSnapshot(new SnapshotVersion(1), web, hash(web),
                CLOCK.instant());
        for (TestStore store : List.of(
                new TestStore(snapshot(2, ios), content(false)),
                new TestStore(snapshot(2, ios), ios))) {
            snapshotService(store, version -> Optional.of(target)).rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1)));
            assertFalse(store.active);
            assertEquals(1, store.clients.writeCount);
            assertEquals(1, store.endpoints.writeCount);
            assertEquals(1, store.publishCount);
            assertEquals(web.apps().asMap(), store.clients.value().asMap());
            assertEquals("WEB", store.capture().snapshot().content().apps().find(APP_ID).orElseThrow().platform());
            assertEquals(3, store.capture().snapshot().version().value());
        }
    }

    @Test
    void shouldRejectExpiredActiveAndGraceCredentialsBeforeRecoveryWrites() {
        for (boolean graceExpired : List.of(false, true)) {
            IngressContent targetContent = credentialContent(graceExpired, CLOCK.instant());
            IngressSnapshot target = new IngressSnapshot(new SnapshotVersion(1), targetContent,
                    hash(targetContent), CLOCK.instant().minusSeconds(60));
            for (boolean alreadyBlocked : List.of(false, true)) {
                TestStore store = new TestStore(snapshot(2, content(false)), content(false));
                store.active = alreadyBlocked;
                assertThrows(DomainException.class, () -> snapshotService(store, version -> Optional.of(target))
                        .rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1))));
                assertEquals(alreadyBlocked, store.active);
                assertEquals(0, store.clients.writeCount);
                assertEquals(0, store.clientAccess.writeCount);
                assertEquals(0, store.endpoints.writeCount);
                assertEquals(0, store.publishCount);
            }
        }
    }

    @Test
    void shouldCaptureRecoveryBusinessTimeOnceBeforeWritingDefinitions() {
        IngressContent targetContent = credentialContent(false, CLOCK.instant().plusSeconds(1));
        IngressSnapshot target = new IngressSnapshot(new SnapshotVersion(1), targetContent,
                hash(targetContent), CLOCK.instant().minusSeconds(60));
        TestStore store = new TestStore(snapshot(2, content(false)), content(false));
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        Clock advancing = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() {
                assertEquals(0, store.clients.writeCount);
                assertEquals(0, store.clientAccess.writeCount);
                return CLOCK.instant().plusSeconds(reads.getAndIncrement() * 120L);
            }
        };
        snapshotService(store, version -> Optional.of(target), advancing)
                .rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1)));
        assertEquals(1, reads.get());
        assertEquals(CLOCK.instant(), store.capture().snapshot().publishedAt());
        assertFalse(store.active);
    }

    private static IngressContent credentialContent(boolean graceExpired, Instant expiry) {
        Credential active = new Credential(new CredentialId("active"), "secret-ref",
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, Instant.EPOCH,
                graceExpired ? expiry.plusSeconds(3600) : expiry);
        Credential grace = graceExpired ? new Credential(new CredentialId("grace"), "secret-ref",
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, Instant.EPOCH, expiry) : null;
        IngressContent base = content(true);
        Client client = new Client(CLIENT_ID, ClientStatus.ENABLED,
                new ClientAccess(Set.of(), new Credentials(active, grace)));
        return new IngressContent(base.apps(), new ClientRegistry(List.of(client)),
                base.endpoints(), base.capabilities());
    }

    @Test
    void shouldUpdateOnlyRequestedDefinition() {
        TestStore store = new TestStore(snapshot(1, content(true)), content(true));
        store.endpoints.failOnLoad = true;

        appService(store).save(new com.cloud.gateway.application.configuration.editing.SaveAppAccessCommand(store.clients.revision(), APP_ID, "IOS", Set.of()));

        assertEquals("IOS", store.clients.value().find(APP_ID).orElseThrow().platform());
        assertEquals(0, store.endpoints.readCount);
        assertEquals(1, store.clients.readCount);
        assertEquals(0, store.clientAccess.readCount);
    }

    @Test
    void shouldPublishDefinitionsAsOneIncrementedSnapshot() {
        TestStore store = new TestStore(snapshot(1, content(true)), content(false));

        com.cloud.gateway.application.configuration.publication.IngressSnapshotUpdateOutput output = snapshotService(store, version -> Optional.empty())
                .publish(new com.cloud.gateway.application.configuration.publication.PublishAccessSnapshotCommand());

        assertEquals(2, output.snapshotVersion().value());
        assertEquals("disabled", output.contentHash());
        assertFalse(store.capture().snapshot().content().endpoints()
                .find(new EndpointId("LOGIN")).orElseThrow().enabled());
    }

    @Test
    void shouldRejectPublishWhenDefinitionsHaveNoChanges() {
        TestStore store = new TestStore(snapshot(1, content(true)), content(true));

        assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.empty())
                .publish(new com.cloud.gateway.application.configuration.publication.PublishAccessSnapshotCommand()));
        assertEquals(0, store.publishCount);
    }

    @Test
    void shouldRejectMixedSnapshotWhenOneDefinitionChangesDuringPublish() {
        TestStore store = new TestStore(snapshot(1, content(true)), content(false));
        store.endpoints.changeRevisionOnSecondRead = true;

        assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.empty())
                .publish(new com.cloud.gateway.application.configuration.publication.PublishAccessSnapshotCommand()));
        assertEquals(0, store.publishCount);
    }

    @Test
    void shouldNotBlockWritesWhenRollbackTargetIsMissingAndAllowAnotherTarget() {
        TestStore store = new TestStore(snapshot(2, content(false)), content(false));
        com.cloud.gateway.application.configuration.publication.AccessSnapshotHistory history = version -> version.value() == 1
                ? Optional.empty() : Optional.of(snapshot(version.value(), content(true)));
        com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService service = snapshotService(store, history);

        assertThrows(ApplicationException.class,
                () -> service.rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1))));
        assertFalse(store.active);

        com.cloud.gateway.application.configuration.publication.IngressSnapshotUpdateOutput output = service.rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(7)));

        assertEquals(3, output.snapshotVersion().value());
        assertFalse(store.active);
        assertEquals(List.of("rollback", "publish", "rollback", "publish"), store.lockExecutor.scenes);
    }

    @Test
    void shouldPreserveExistingRecoveryBlockWhenAnotherTargetIsMissing() {
        TestStore store = new TestStore(snapshot(2, content(false)), content(false));
        store.active = true;
        assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.empty())
                .rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(99))));
        assertTrue(store.active);
        assertEquals(0, store.endpoints.writeCount);
        assertEquals(0, store.publishCount);
    }

    @Test
    void shouldNotActivateRecoveryWhenEitherLockIsUnavailable() {
        for (String scene : List.of("rollback", "publish")) {
            TestStore store = new TestStore(snapshot(2, content(false)), content(false));
            store.lockExecutor.unavailableScene = scene;
            assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.of(snapshot(1, content(true))))
                    .rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1))));
            assertFalse(store.active);
            assertEquals(0, store.endpoints.writeCount);
            assertEquals(0, store.publishCount);
        }
    }

    @Test
    void shouldNotActivateRecoveryWhenTargetViolatesHeaderRules() {
        CapabilityBundleId id = new CapabilityBundleId("invalid-header");
        HeaderPolicy headers =
                new HeaderPolicy(Set.of(), Map.of("X-Unregistered", "value"),
                        Set.of(), null);
        CapabilityComposition policies = new CapabilityComposition(
                Map.of(id, new CapabilityBundle(id, java.util.List.of(headers))), List.of());
        IngressContent targetContent = withPolicies(content(true), policies);
        IngressSnapshot target = new IngressSnapshot(new SnapshotVersion(1), targetContent,
                hash(targetContent), CLOCK.instant());
        TestStore store = new TestStore(snapshot(2, content(false)), content(false));
        assertThrows(DomainException.class, () -> snapshotService(store, version -> Optional.of(target))
                .rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1))));
        assertFalse(store.active);
        assertEquals(0, store.endpoints.writeCount);
        assertEquals(0, store.publishCount);
    }

    @Test
    void shouldResumeRollbackAfterSnapshotWasPublishedButStateClearFailed() {
        TestStore store = new TestStore(snapshot(2, content(false)), content(false));
        store.failClear = true;
        com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService service = snapshotService(store, version -> Optional.of(snapshot(1, content(true))));

        assertThrows(RuntimeException.class,
                () -> service.rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1))));
        assertEquals(1, store.publishCount);
        assertTrue(store.active);

        store.failClear = false;
        com.cloud.gateway.application.configuration.publication.IngressSnapshotUpdateOutput output = service.rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1)));

        assertEquals(3, output.snapshotVersion().value());
        assertEquals(1, store.publishCount);
        assertFalse(store.active);
    }

    @Test
    void shouldClearPublishedRecoveryAfterCredentialsExpireWithoutRepublishing() {
        for (boolean graceExpires : List.of(false, true)) {
            IngressContent targetContent = credentialContent(graceExpires, CLOCK.instant().plusSeconds(1));
            IngressSnapshot target = new IngressSnapshot(new SnapshotVersion(1), targetContent,
                    hash(targetContent), CLOCK.instant().minusSeconds(60));
            TestStore store = new TestStore(snapshot(2, content(false)), content(false));
            store.failClear = true;
            assertThrows(RuntimeException.class, () -> snapshotService(store, version -> Optional.of(target))
                    .rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1))));
            assertEquals(1, store.publishCount);
            assertTrue(store.active);
            IngressSnapshot published = store.capture().snapshot();
            int credentialWrites = store.clientAccess.writeCount;
            int endpointWrites = store.endpoints.writeCount;

            store.failClear = false;
            Clock later = Clock.fixed(CLOCK.instant().plusSeconds(120), ZoneOffset.UTC);
            com.cloud.gateway.application.configuration.publication.IngressSnapshotUpdateOutput output = snapshotService(store, version -> Optional.of(target), later)
                    .rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1)));

            assertFalse(store.active);
            assertEquals(1, store.publishCount);
            assertEquals(published, store.capture().snapshot());
            assertEquals(published.version().value(), output.snapshotVersion().value());
            assertEquals(published.publishedAt(), store.capture().snapshot().publishedAt());
            assertEquals(credentialWrites, store.clientAccess.writeCount);
            assertEquals(endpointWrites, store.endpoints.writeCount);
        }
    }

    @Test
    void shouldResumeRollbackWithoutRewritingAlreadyRestoredDefinitions() {
        TestStore store = new TestStore(snapshot(2, content(false)), content(false));
        IngressContent targetContent = withPolicies(content(true, Map.of(APP_ID, app("WEB"))),
                configuredPolicies());
        IngressSnapshot target = new IngressSnapshot(new SnapshotVersion(1), targetContent,
                "enabled", Instant.parse("2026-08-24T00:00:01Z"));
        store.policies.rejectNextWrite = true;
        com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService service = snapshotService(store, version -> Optional.of(target));

        assertThrows(ApplicationException.class,
                () -> service.rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1))));
        assertEquals(1, store.clients.writeCount);
        assertTrue(store.active);

        service.rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1)));

        assertEquals(1, store.clients.writeCount);
        assertFalse(store.active);
    }

    @Test
    void shouldBlockOrdinaryPublishWhileRollbackStateExists() {
        TestStore store = new TestStore(snapshot(1, content(true)), content(false));
        store.active = true;

        assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.empty())
                .publish(new com.cloud.gateway.application.configuration.publication.PublishAccessSnapshotCommand()));
    }

    @Test
    void shouldMapUnavailablePublishLockToPublishLockError() {
        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> new AccessPublishLock(new UnavailableLockExecutor()).execute(() -> Boolean.TRUE));

        assertEquals(ApplicationError.APP_POLICY_PUBLISH_LOCK_UNAVAILABLE.errorCode(), exception.getErrorCode());
    }

    @Test
    void shouldMapUnavailableRollbackLockToRollbackLockError() {
        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> new com.cloud.gateway.application.configuration.publication.AccessRollbackLock(new UnavailableLockExecutor()).execute(() -> Boolean.TRUE));

        assertEquals(ApplicationError.APP_POLICY_ROLLBACK_LOCK_UNAVAILABLE.errorCode(), exception.getErrorCode());
    }

    private static com.cloud.gateway.application.configuration.editing.AppAccessCommandService appService(TestStore store) {
        return new com.cloud.gateway.application.configuration.editing.AppAccessCommandService(store.clients, store, new AccessPublishLock(store.lockExecutor));
    }

    @Test
    void shouldPreserveCredentialsAndOtherClientsWhenUpdatingAccess() {
        Credentials original = clientCredentials("original");
        Client existing = client("client-a", ClientStatus.ENABLED, Set.of(new AppId("app-a")), original);
        TestStore store = clientStore(existing);
        clientAccessService(store).save(new com.cloud.gateway.application.configuration.editing.SaveClientAccessCommand(store.clientAccess.revision(),
                existing.clientId(), Set.of(new AppId("app-b"))));
        Client saved = store.clientAccess.value().find(existing.clientId()).orElseThrow();
        assertEquals(original, saved.access().credentials());
        assertEquals(ClientStatus.ENABLED, saved.status());
        assertEquals(Set.of(new AppId("app-b")), saved.access().allowedApps());
        assertTrue(store.clientAccess.value().find(new ClientId("other-client")).isPresent());
        assertEquals(0, store.publishCount);
        assertEquals(0, store.endpoints.readCount);
        assertEquals(0, store.clients.readCount);
    }

    @Test
    void shouldPreserveDisabledStatusAndAllowedAppsWhenRotatingCredentials() {
        Client existing = client("client-a", ClientStatus.DISABLED, Set.of(new AppId("app-a")),
                clientCredentials("original"));
        TestStore store = clientStore(existing);
        Credentials replacement = clientCredentials("replacement");
        clientCredentialService(store).save(new com.cloud.gateway.application.configuration.editing.SaveClientCredentialCommand(store.clientAccess.revision(),
                existing.clientId(), replacement));
        Client saved = store.clientAccess.value().find(existing.clientId()).orElseThrow();
        assertEquals(replacement, saved.access().credentials());
        assertEquals(ClientStatus.DISABLED, saved.status());
        assertEquals(Set.of(new AppId("app-a")), saved.access().allowedApps());
        assertEquals(0, store.publishCount);
        assertTrue(store.clientAccess.value().find(new ClientId("other-client")).isPresent());
    }

    @Test
    void shouldShareRevisionAndRejectStaleWritesAcrossBothClientOperations() {
        Client existing = client("client-a", ClientStatus.ENABLED, Set.of(new AppId("app-a")),
                clientCredentials("original"));
        TestStore store = clientStore(existing);
        Revision originalRevision = store.clientAccess.revision();
        clientAccessService(store).save(new com.cloud.gateway.application.configuration.editing.SaveClientAccessCommand(originalRevision,
                existing.clientId(), Set.of()));
        ApplicationException credentialConflict = assertThrows(ApplicationException.class,
                () -> clientCredentialService(store).save(new com.cloud.gateway.application.configuration.editing.SaveClientCredentialCommand(originalRevision,
                        existing.clientId(), clientCredentials("replacement"))));
        assertEquals(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT.errorCode(), credentialConflict.getErrorCode());
        Revision accessRevision = store.clientAccess.revision();
        clientCredentialService(store).save(new com.cloud.gateway.application.configuration.editing.SaveClientCredentialCommand(accessRevision,
                existing.clientId(), clientCredentials("replacement")));
        ApplicationException accessConflict = assertThrows(ApplicationException.class,
                () -> clientAccessService(store).save(new com.cloud.gateway.application.configuration.editing.SaveClientAccessCommand(accessRevision,
                        existing.clientId(), Set.of(new AppId("app-a")))));
        assertEquals(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT.errorCode(), accessConflict.getErrorCode());
        assertEquals(ClientStatus.ENABLED, store.clientAccess.value().find(existing.clientId()).orElseThrow().status());
        assertEquals(clientCredentials("replacement"), store.clientAccess.value().find(existing.clientId()).orElseThrow().access().credentials());
        assertEquals(2, store.clientAccess.writeCount);
    }

    @Test
    void shouldRejectCredentialUpdateForMissingClientWithoutWriting() {
        var store = new TestStore(snapshot(1, content(true)), content(true));
        assertThrows(DomainException.class, () -> clientCredentialService(store).save(
                new com.cloud.gateway.application.configuration.editing.SaveClientCredentialCommand(store.clientAccess.revision(), new ClientId("client-a"), clientCredentials("active"))));
        assertEquals(0, store.clientAccess.writeCount);
        assertTrue(store.clientAccess.value().asMap().isEmpty());
    }

    @Test
    void shouldBlockBothClientOperationsDuringRollbackBeforeLoadingDefinition() {
        Client existing = client("client-a", ClientStatus.ENABLED, Set.of(new AppId("app-a")), null);
        TestStore store = clientStore(existing);
        store.active = true;
        store.clientAccess.failOnLoad = true;
        ApplicationException accessFailure = assertThrows(ApplicationException.class,
                () -> clientAccessService(store).save(new com.cloud.gateway.application.configuration.editing.SaveClientAccessCommand(store.clientAccess.revision(),
                        existing.clientId(), Set.of(new AppId("app-a")))));
        ApplicationException credentialFailure = assertThrows(ApplicationException.class,
                () -> clientCredentialService(store).save(new com.cloud.gateway.application.configuration.editing.SaveClientCredentialCommand(store.clientAccess.revision(),
                        existing.clientId(), clientCredentials("active"))));
        assertEquals(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS.errorCode(), accessFailure.getErrorCode());
        assertEquals(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS.errorCode(), credentialFailure.getErrorCode());
        assertEquals(0, store.clientAccess.readCount);
        assertEquals(0, store.clientAccess.writeCount);
    }

    @Test
    void shouldRejectCompareAndSetFailureForBothClientOperationsWithoutLosingExistingState() {
        Client existing = client("client-a", ClientStatus.ENABLED, Set.of(new AppId("app-a")),
                clientCredentials("original"));
        TestStore store = clientStore(existing);
        store.clientAccess.rejectNextWrite = true;
        assertThrows(ApplicationException.class, () -> clientAccessService(store).save(
                new com.cloud.gateway.application.configuration.editing.SaveClientAccessCommand(store.clientAccess.revision(), existing.clientId(), Set.of())));
        store.clientAccess.rejectNextWrite = true;
        assertThrows(ApplicationException.class, () -> clientCredentialService(store).save(
                new com.cloud.gateway.application.configuration.editing.SaveClientCredentialCommand(store.clientAccess.revision(), existing.clientId(), clientCredentials("replacement"))));
        assertEquals(existing, store.clientAccess.value().find(existing.clientId()).orElseThrow());
        assertEquals(0, store.clientAccess.writeCount);
    }

    private static com.cloud.gateway.application.configuration.editing.ClientAccessCommandService clientAccessService(TestStore store) {
        return new com.cloud.gateway.application.configuration.editing.ClientAccessCommandService(store.clientAccess, store, new AccessPublishLock(store.lockExecutor));
    }

    private static com.cloud.gateway.application.configuration.editing.ClientCredentialCommandService clientCredentialService(TestStore store) {
        return new com.cloud.gateway.application.configuration.editing.ClientCredentialCommandService(store.clientAccess, store, new AccessPublishLock(store.lockExecutor));
    }

    private static TestStore clientStore(Client client) {
        IngressContent source = content(true);
        Client other = new Client(new ClientId("other-client"), ClientStatus.ENABLED, new ClientAccess(Set.of(), null));
        return new TestStore(snapshot(1, content(true)), new IngressContent(source.apps(),
                new ClientRegistry(List.of(client, other)), source.endpoints(), source.capabilities()));
    }

    private static Credentials clientCredentials(String id) {
        return new Credentials(new Credential(new CredentialId(id), "secret-reference",
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, Instant.EPOCH,
                Instant.parse("2099-01-01T00:00:00Z")), null);
    }

    private static com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService snapshotService(TestStore store, com.cloud.gateway.application.configuration.publication.AccessSnapshotHistory history) {
        return snapshotService(store, history, CLOCK);
    }

    private static com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService snapshotService(TestStore store, com.cloud.gateway.application.configuration.publication.AccessSnapshotHistory history, Clock clock) {
        com.cloud.gateway.application.configuration.publication.AccessDefinitions definitions = new com.cloud.gateway.application.configuration.publication.AccessDefinitions(
                store.endpoints, store.clients, store.clientAccess, store.policies);
        HeaderAdmissionRules rules =
                HeaderAdmissionRules.registeredOnly();
        com.cloud.gateway.application.configuration.publication.AccessSnapshotPublication publication = new com.cloud.gateway.application.configuration.publication.AccessSnapshotPublication(store,
                new com.cloud.gateway.application.configuration.publication.AccessSnapshotAssembler(AccessCommandServicesTest::hash, rules, com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry()), clock);
        AccessPublishLock publishLock = new AccessPublishLock(store.lockExecutor);
        return new com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService(
                new com.cloud.gateway.application.configuration.publication.AccessPublicationCommandService(definitions, store, publication, store,
                        publishLock),
                new com.cloud.gateway.application.configuration.publication.AccessRollbackCommandService(definitions, store, publication, history, store,
                        AccessCommandServicesTest::hash, publishLock, new com.cloud.gateway.application.configuration.publication.AccessRollbackLock(store.lockExecutor)));
    }

    private static String hash(IngressContent content) {
        return (content.endpoints().find(new EndpointId("LOGIN")).orElseThrow().enabled() ? "enabled" : "disabled")
                + (content.apps().asMap().isEmpty() ? "" : content.apps().asMap().toString())
                + (content.capabilities().bundles().isEmpty() ? "" : content.capabilities().toString());
    }

    private static CapabilityComposition configuredPolicies() {
        var id = new CapabilityBundleId("standard");
        return new CapabilityComposition(Map.of(id, new CapabilityBundle(id, java.util.List.of(new TimeoutPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3))))), List.of());
    }

    private static IngressContent withPolicies(IngressContent source, CapabilityComposition policies) {
        return new IngressContent(source.apps(), source.clients(), source.endpoints(),
                policies);
    }

    @Test void shouldSaveOnlyCapabilityCompositionWithoutPublishing() {
        var store = new TestStore(snapshot(1, content(true)), content(true));
        store.endpoints.failOnLoad = true;
        CapabilityComposition policies = configuredPolicies();
        var service = new com.cloud.gateway.application.configuration.editing.CapabilityCompositionCommandService(store.policies, store, new AccessPublishLock(store.lockExecutor), HeaderAdmissionRules.registeredOnly(), com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry());
        service.save(new com.cloud.gateway.application.configuration.editing.SaveCapabilityCompositionCommand(store.policies.revision(), policies));
        assertEquals(policies, store.policies.value());
        assertEquals(0, store.publishCount);
        assertEquals(0, store.endpoints.readCount);
        assertThrows(ApplicationException.class, () -> service.save(new com.cloud.gateway.application.configuration.editing.SaveCapabilityCompositionCommand(new Revision("stale"), policies)));
        store.active = true;
        assertThrows(ApplicationException.class, () -> service.save(new com.cloud.gateway.application.configuration.editing.SaveCapabilityCompositionCommand(store.policies.revision(), policies)));
    }

    @Test void shouldPublishCapabilityCompositionOnlyChanges() {
        CapabilityComposition policies = configuredPolicies();
        var store = new TestStore(snapshot(1, content(true)), withPolicies(content(true), policies));
        snapshotService(store, version -> Optional.empty()).publish(new com.cloud.gateway.application.configuration.publication.PublishAccessSnapshotCommand());
        assertEquals(policies, store.capture().snapshot().content().capabilities());
        assertEquals(2, store.policies.readCount);
    }

    @Test void shouldRejectUnadmittedHeadersBeforeSavingOrPublishing() {
        var id = new CapabilityBundleId("invalid-header");
        var headers = new HeaderPolicy(Set.of(),
                Map.of("X-Unregistered", "value"), Set.of(), null);
        var policies = new CapabilityComposition(Map.of(id,
                new CapabilityBundle(id, java.util.List.of(headers))), List.of());
        var store = new TestStore(snapshot(1, content(true)), content(true));
        var service = new com.cloud.gateway.application.configuration.editing.CapabilityCompositionCommandService(store.policies, store, new AccessPublishLock(store.lockExecutor), HeaderAdmissionRules.registeredOnly(), com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry());
        assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                () -> service.save(new com.cloud.gateway.application.configuration.editing.SaveCapabilityCompositionCommand(store.policies.revision(), policies)));
        assertTrue(store.policies.value().bundles().isEmpty());
        var publicationStore = new TestStore(snapshot(1, content(true)), withPolicies(content(true), policies));
        assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                () -> snapshotService(publicationStore, version -> Optional.empty()).publish(new com.cloud.gateway.application.configuration.publication.PublishAccessSnapshotCommand()));
        assertEquals(0, publicationStore.publishCount);
    }

    @Test void shouldRejectCapabilityCompositionRevisionChangeDuringPublish() {
        var store = new TestStore(snapshot(1, content(true)), withPolicies(content(true), configuredPolicies()));
        store.policies.changeRevisionOnSecondRead = true;
        assertThrows(ApplicationException.class,
                () -> snapshotService(store, version -> Optional.empty()).publish(new com.cloud.gateway.application.configuration.publication.PublishAccessSnapshotCommand()));
        assertEquals(0, store.publishCount);
    }

    @Test void shouldResumeRollbackWhenCapabilityCompositionRestoreFails() {
        IngressContent targetContent = withPolicies(content(true), configuredPolicies());
        var target = new IngressSnapshot(new SnapshotVersion(1), targetContent, hash(targetContent),
                CLOCK.instant());
        var store = new TestStore(snapshot(2, content(false)), content(false));
        store.policies.rejectNextWrite = true;
        com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService service = snapshotService(store, version -> Optional.of(target));
        assertThrows(ApplicationException.class, () -> service.rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1))));
        assertTrue(store.active);
        assertEquals(0, store.publishCount);
        service.rollback(new com.cloud.gateway.application.configuration.publication.RollbackAccessSnapshotCommand(new SnapshotVersion(1)));
        assertEquals(targetContent.capabilities(), store.capture().snapshot().content().capabilities());
        assertFalse(store.active);
        assertEquals(1, store.policies.writeCount);
    }

    private static IngressSnapshot snapshot(long version, IngressContent content) {
        return new IngressSnapshot(new SnapshotVersion(version), content, hash(content),
                Instant.parse("2026-08-24T00:00:00Z").plusSeconds(version));
    }

    private static IngressContent content(boolean enabled) {
        return content(enabled, Map.of());
    }

    private static IngressContent content(boolean enabled, Map<AppId, App> apps) {
        EndpointRegistry catalog = new EndpointRegistry(List.of(new Endpoint(new EndpointId("LOGIN"),
                new com.cloud.gateway.domain.endpoint.ServiceId("service"),
                new com.cloud.gateway.domain.endpoint.RouteId("service"),
                List.of(new EndpointMatcher("POST", "/login")), Set.of(),
                enabled ? EndpointStatus.ENABLED : EndpointStatus.DISABLED, route())));
        return new IngressContent(new AppRegistry(apps.values()), new ClientRegistry(List.of()),
                catalog, CapabilityComposition.empty());
    }

    private static EndpointAccess route() {
        return new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of());
    }

    private static App app(String platform) {
        return new App(APP_ID, platform, AppStatus.ENABLED, new AppAccess(Set.of()));
    }

    private static Client client(String id, ClientStatus status, java.util.Set<AppId> allowedApps,
            Credentials credentials) {
        return new Client(new ClientId(id), status, new ClientAccess(allowedApps, credentials));
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

        public RevisionedAccessDefinition<T> load() {
            readCount++;
            if (failOnLoad) {
                throw new AssertionError("unrelated definition must not be loaded");
            }
            if (changeRevisionOnSecondRead && readCount == 2) {
                revision++;
            }
            return new RevisionedAccessDefinition<>(value, revision());
        }

        public Revision compareAndSet(Revision expectedSourceRevision, T next) {
            if (rejectNextWrite) {
                rejectNextWrite = false;
                return null;
            }
            if (!revision().equals(expectedSourceRevision)) {
                return null;
            }
            value = next;
            revision++;
            writeCount++;
            return revision();
        }

        Revision revision() { return new Revision("definition-" + revision); }
        T value() { return value; }
    }

    private static final class EndpointDefinitions extends TestDefinition<EndpointRegistry>
            implements com.cloud.gateway.application.configuration.definition.WritableEndpointAccessDefinitionRepository {
        EndpointDefinitions(EndpointRegistry value) { super(value); }
    }

    private static final class AppDefinitions extends TestDefinition<AppRegistry>
            implements com.cloud.gateway.application.configuration.definition.WritableAppAccessDefinitionRepository {
        AppDefinitions(AppRegistry value) { super(value); }
    }

    private static final class ClientAccessDefinitions
            extends TestDefinition<ClientRegistry>
            implements com.cloud.gateway.application.configuration.definition.WritableClientAccessDefinitionRepository {
        ClientAccessDefinitions(ClientRegistry value) {
            super(value);
        }
    }

    private static final class ConfigurationDefinitions extends TestDefinition<CapabilityComposition>
            implements com.cloud.gateway.application.configuration.definition.WritableCapabilityCompositionDefinitionRepository {
        ConfigurationDefinitions(CapabilityComposition value) { super(value); }
    }

    private static final class TestStore implements com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer, com.cloud.gateway.application.configuration.publication.AccessSnapshotPublisher,
            com.cloud.gateway.application.configuration.publication.AccessRollbackState {
        private RevisionedAccessSnapshot snapshot;
        private final EndpointDefinitions endpoints;
        private final AppDefinitions clients;
        private final ClientAccessDefinitions clientAccess;
        private final ConfigurationDefinitions policies;
        private int publishCount;
        private boolean active;
        private boolean failClear;
        private final RecordingLockExecutor lockExecutor = new RecordingLockExecutor();

        private TestStore(IngressSnapshot snapshot, IngressContent definitions) {
            this.snapshot = new RevisionedAccessSnapshot(snapshot, new Revision("snapshot-1"));
            endpoints = new EndpointDefinitions(definitions.endpoints());
            clients = new AppDefinitions(definitions.apps());
            clientAccess = new ClientAccessDefinitions(definitions.clients());
            policies = new ConfigurationDefinitions(definitions.capabilities());
        }

        private void disableApp(AppId id) {
            App app = clients.value().find(id).orElseThrow();
            Map<AppId, App> updated = new LinkedHashMap<>(clients.value().asMap());
            updated.put(id, new App(app.appId(), app.platform(), AppStatus.DISABLED, app.access()));
            clients.compareAndSet(clients.revision(), new AppRegistry(updated.values()));
        }

        @Override public RevisionedAccessSnapshot capture() { return snapshot; }

        @Override public boolean compareAndSet(Revision expectedSourceRevision, IngressSnapshot next) {
            if (!snapshot.sourceRevision().equals(expectedSourceRevision)) {
                return false;
            }
            snapshot = new RevisionedAccessSnapshot(next, new Revision("snapshot-" + next.version().value()));
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
        private String unavailableScene;

        @Override public <T> Optional<T> execute(LockContext context, Callable<T> callable) throws Exception {
            scenes.add(context.lockNames()[0]);
            if (context.lockNames()[0].equals(unavailableScene)) {
                return Optional.empty();
            }
            return Optional.ofNullable(callable.call());
        }
    }

    private static final class UnavailableLockExecutor implements LockExecutor {
        @Override public <T> Optional<T> execute(LockContext context, Callable<T> callable) {
            return Optional.empty();
        }
    }
}
