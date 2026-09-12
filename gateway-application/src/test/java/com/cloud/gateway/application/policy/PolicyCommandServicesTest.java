package com.cloud.gateway.application.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.framework.lock.LockContext;
import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.policy.bundle.*;
import com.cloud.gateway.domain.policy.capability.TimeoutPolicy;
import java.time.Duration;
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
    void shouldAllowPlatformChangeWhenSavingExistingApp() {
        AppId id = new AppId("existing-app");
        AppPolicy existing = new AppPolicy(id, "WEB", Set.of(), PolicyStatus.ENABLED);
        IngressAccessContent original = content(true, Map.of(id, existing));
        TestStore store = new TestStore(new IngressAccessSnapshot(1, original, hash(original), CLOCK.instant()), original);
        AppPolicy changed = new AppPolicy(id, "IOS", Set.of(), PolicyStatus.ENABLED);
        appService(store).save(new SaveAppPolicyCommand(store.clients.revision(), changed));
        assertEquals(changed, store.clients.value().get(id));
        assertEquals(1, store.clients.writeCount);
        assertEquals(existing, store.current().snapshot().content().appPolicies().get(id));
        assertEquals(0, store.publishCount);
    }

    @Test
    void shouldPublishPlatformChangeFromDirectDefinitionEdit() {
        AppId id = new AppId("existing-app");
        IngressAccessContent original = content(true, Map.of(id,
                new AppPolicy(id, "WEB", Set.of(), PolicyStatus.ENABLED)));
        IngressAccessContent edited = content(true, Map.of(id,
                new AppPolicy(id, "IOS", Set.of(), PolicyStatus.ENABLED)));
        TestStore store = new TestStore(new IngressAccessSnapshot(1, original, hash(original), CLOCK.instant()), edited);
        snapshotService(store, PolicySnapshotHistory.empty()).publish(new PublishPolicySnapshotCommand());
        assertEquals(1, store.publishCount);
        assertEquals(2, store.current().snapshot().version());
        assertEquals("IOS", store.current().snapshot().content().appPolicies().get(id).platform());
        assertEquals("WEB", original.appPolicies().get(id).platform());
    }

    @Test
    void shouldRollbackPlatformChangesAgainstRunningAndEditedApps() {
        AppId id = new AppId("existing-app");
        IngressAccessContent web = content(true, Map.of(id, new AppPolicy(id, "WEB", Set.of(), PolicyStatus.ENABLED)));
        IngressAccessContent ios = content(false, Map.of(id, new AppPolicy(id, "IOS", Set.of(), PolicyStatus.ENABLED)));
        IngressAccessSnapshot target = new IngressAccessSnapshot(1, web, hash(web), CLOCK.instant());
        for (TestStore store : List.of(
                new TestStore(new IngressAccessSnapshot(2, ios, hash(ios), CLOCK.instant()), content(false)),
                new TestStore(snapshot(2, false), ios))) {
            snapshotService(store, version -> Optional.of(target)).rollback(new RollbackPolicySnapshotCommand(1));
            assertFalse(store.active);
            assertEquals(1, store.clients.writeCount);
            assertEquals(1, store.routes.writeCount);
            assertEquals(1, store.publishCount);
            assertEquals(web.appPolicies(), store.clients.value());
            assertEquals("WEB", store.current().snapshot().content().appPolicies().get(id).platform());
            assertEquals(3, store.current().snapshot().version());
        }
    }

    @Test
    void shouldRejectExpiredActiveAndGraceCredentialsBeforeRecoveryWrites() {
        for (boolean graceExpired : List.of(false, true)) {
            IngressAccessContent targetContent = credentialContent(graceExpired, CLOCK.instant());
            IngressAccessSnapshot target = new IngressAccessSnapshot(1, targetContent, hash(targetContent),
                    CLOCK.instant().minusSeconds(60));
            for (boolean alreadyBlocked : List.of(false, true)) {
                TestStore store = new TestStore(snapshot(2, false), content(false));
                store.active = alreadyBlocked;
                assertThrows(DomainException.class, () -> snapshotService(store, version -> Optional.of(target))
                        .rollback(new RollbackPolicySnapshotCommand(1)));
                assertEquals(alreadyBlocked, store.active);
                assertEquals(0, store.clients.writeCount);
                assertEquals(0, store.clientAccess.writeCount);
                assertEquals(0, store.routes.writeCount);
                assertEquals(0, store.publishCount);
            }
        }
    }

    @Test
    void shouldCaptureRecoveryBusinessTimeOnceBeforeWritingDefinitions() {
        IngressAccessContent targetContent = credentialContent(false, CLOCK.instant().plusSeconds(1));
        IngressAccessSnapshot target = new IngressAccessSnapshot(1, targetContent, hash(targetContent),
                CLOCK.instant().minusSeconds(60));
        TestStore store = new TestStore(snapshot(2, false), content(false));
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
                .rollback(new RollbackPolicySnapshotCommand(1));
        assertEquals(1, reads.get());
        assertEquals(CLOCK.instant(), store.current().snapshot().publishedAt());
        assertFalse(store.active);
    }

    private static IngressAccessContent credentialContent(boolean graceExpired, Instant expiry) {
        ClientId clientId = new ClientId("client-a");
        Credential active = new Credential(new CredentialId("active"), clientId,
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, "secret-ref", Instant.EPOCH,
                graceExpired ? expiry.plusSeconds(3600) : expiry);
        Credential grace = graceExpired ? new Credential(new CredentialId("grace"), clientId,
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, "secret-ref", Instant.EPOCH, expiry) : null;
        IngressAccessContent base = content(true);
        return new IngressAccessContent(base.endpointRegistry(), base.appPolicies(),
                Map.of(clientId, new ClientPolicy(clientId, Set.of(), new Credentials(active, grace), ClientStatus.ENABLED)),
                base.endpointPolicies(), base.policies());
    }

    @Test
    void shouldUpdateOnlyRequestedDefinition() {
        TestStore store = new TestStore(snapshot(1, true), content(true));
        store.endpoints.failOnLoad = true;
        AppPolicy policy = new AppPolicy(
                new AppId("new-app"), "IOS", Set.of(), PolicyStatus.ENABLED);

        appService(store).save(new SaveAppPolicyCommand(store.clients.revision(), policy));

        assertEquals(policy, store.clients.value().get(policy.appId()));
        assertEquals(0, store.endpoints.readCount);
        assertEquals(1, store.clients.readCount);
        assertEquals(0, store.clientAccess.readCount);
        assertEquals(0, store.routes.readCount);
    }

    @Test
    void shouldPublishDefinitionsAsOneIncrementedSnapshot() {
        TestStore store = new TestStore(snapshot(1, true), content(false));

        PolicyPublicationOutput output = snapshotService(store, version -> Optional.empty())
                .publish(new PublishPolicySnapshotCommand());

        assertEquals(2, output.snapshotVersion());
        assertEquals("disabled", output.contentHash());
        assertFalse(store.current().snapshot().content().endpointPolicies()
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
    void shouldNotBlockWritesWhenRollbackTargetIsMissingAndAllowAnotherTarget() {
        TestStore store = new TestStore(snapshot(2, false), content(false));
        PolicySnapshotHistory history = version -> version == 1
                ? Optional.empty() : Optional.of(snapshot(version, true));
        PolicySnapshotCommandService service = snapshotService(store, history);

        assertThrows(ApplicationException.class,
                () -> service.rollback(new RollbackPolicySnapshotCommand(1)));
        assertFalse(store.active);

        PolicyPublicationOutput output = service.rollback(new RollbackPolicySnapshotCommand(7));

        assertEquals(3, output.snapshotVersion());
        assertFalse(store.active);
        assertEquals(List.of("rollback", "publish", "rollback", "publish"), store.lockExecutor.scenes);
    }

    @Test
    void shouldPreserveExistingRecoveryBlockWhenAnotherTargetIsMissing() {
        TestStore store = new TestStore(snapshot(2, false), content(false));
        store.active = true;
        assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.empty())
                .rollback(new RollbackPolicySnapshotCommand(99)));
        assertTrue(store.active);
        assertEquals(0, store.routes.writeCount);
        assertEquals(0, store.publishCount);
    }

    @Test
    void shouldNotActivateRecoveryWhenEitherLockIsUnavailable() {
        for (String scene : List.of("rollback", "publish")) {
            TestStore store = new TestStore(snapshot(2, false), content(false));
            store.lockExecutor.unavailableScene = scene;
            assertThrows(ApplicationException.class, () -> snapshotService(store, version -> Optional.of(snapshot(1, true)))
                    .rollback(new RollbackPolicySnapshotCommand(1)));
            assertFalse(store.active);
            assertEquals(0, store.routes.writeCount);
            assertEquals(0, store.publishCount);
        }
    }

    @Test
    void shouldNotActivateRecoveryWhenTargetViolatesHeaderRules() {
        PolicyBundleId id = new PolicyBundleId("invalid-headers");
        com.cloud.gateway.domain.policy.header.HeaderPolicy headers =
                new com.cloud.gateway.domain.policy.header.HeaderPolicy(Set.of(), Map.of("X-Unregistered", "value"),
                        Set.of(), null);
        PolicyComposition policies = new PolicyComposition(
                Map.of(id, new PolicyBundle(id, null, null, null, null, headers)), List.of());
        IngressAccessContent targetContent = withPolicies(content(true), policies);
        IngressAccessSnapshot target = new IngressAccessSnapshot(1, targetContent, hash(targetContent), CLOCK.instant());
        TestStore store = new TestStore(snapshot(2, false), content(false));
        assertThrows(DomainException.class, () -> snapshotService(store, version -> Optional.of(target))
                .rollback(new RollbackPolicySnapshotCommand(1)));
        assertFalse(store.active);
        assertEquals(0, store.routes.writeCount);
        assertEquals(0, store.publishCount);
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
    void shouldClearPublishedRecoveryAfterCredentialsExpireWithoutRepublishing() {
        for (boolean graceExpires : List.of(false, true)) {
            IngressAccessContent targetContent = credentialContent(graceExpires, CLOCK.instant().plusSeconds(1));
            IngressAccessSnapshot target = new IngressAccessSnapshot(1, targetContent, hash(targetContent),
                    CLOCK.instant().minusSeconds(60));
            TestStore store = new TestStore(snapshot(2, false), content(false));
            store.failClear = true;
            assertThrows(RuntimeException.class, () -> snapshotService(store, version -> Optional.of(target))
                    .rollback(new RollbackPolicySnapshotCommand(1)));
            assertEquals(1, store.publishCount);
            assertTrue(store.active);
            IngressAccessSnapshot published = store.current().snapshot();
            int credentialWrites = store.clientAccess.writeCount;
            int routeWrites = store.routes.writeCount;

            store.failClear = false;
            Clock later = Clock.fixed(CLOCK.instant().plusSeconds(120), ZoneOffset.UTC);
            PolicyPublicationOutput output = snapshotService(store, version -> Optional.of(target), later)
                    .rollback(new RollbackPolicySnapshotCommand(1));

            assertFalse(store.active);
            assertEquals(1, store.publishCount);
            assertEquals(published, store.current().snapshot());
            assertEquals(published.version(), output.snapshotVersion());
            assertEquals(published.publishedAt(), store.current().snapshot().publishedAt());
            assertEquals(credentialWrites, store.clientAccess.writeCount);
            assertEquals(routeWrites, store.routes.writeCount);
        }
    }

    @Test
    void shouldResumeRollbackWithoutRewritingAlreadyRestoredDefinitions() {
        TestStore store = new TestStore(snapshot(2, false), content(false));
        IngressAccessContent targetContent = content(true, Map.of(new AppId("restored-app"),
                new AppPolicy(
                        new AppId("restored-app"), "WEB", Set.of(), PolicyStatus.ENABLED)));
        IngressAccessSnapshot target = new IngressAccessSnapshot(1, targetContent, "enabled",
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

    private static AppPolicyCommandService appService(TestStore store) {
        return new AppPolicyCommandService(store.clients, store, new PolicyPublishLock(store.lockExecutor));
    }

    @Test
    void shouldPreserveCredentialsAndOtherClientsWhenUpdatingAccess() {
        Credentials original = clientCredentials("original");
        var existing = new ClientPolicy(new ClientId("client-a"), Set.of(new AppId("app-a")), original, ClientStatus.ENABLED);
        TestStore store = clientStore(existing);
        var requested = new ClientPolicy(existing.clientId(), Set.of(new AppId("app-b")), clientCredentials("must-not-replace"), ClientStatus.DISABLED);
        clientAccessService(store).execute(new SaveClientAccessPolicyCommand(store.clientAccess.revision(), requested));
        ClientPolicy saved = store.clientAccess.value().get(existing.clientId());
        assertEquals(original, saved.credentials());
        assertEquals(requested.status(), saved.status());
        assertEquals(requested.allowedApps(), saved.allowedApps());
        assertTrue(store.clientAccess.value().containsKey(new ClientId("other-client")));
        assertEquals(0, store.publishCount);
        assertEquals(0, store.endpoints.readCount);
        assertEquals(0, store.clients.readCount);
    }

    @Test
    void shouldPreserveDisabledStatusAndAllowedAppsWhenRotatingCredentials() {
        var existing = new ClientPolicy(new ClientId("client-a"), Set.of(new AppId("app-a")), clientCredentials("original"), ClientStatus.DISABLED);
        TestStore store = clientStore(existing);
        Credentials replacement = clientCredentials("replacement");
        clientCredentialService(store).save(new SaveClientCredentialPolicyCommand(store.clientAccess.revision(), existing.clientId(), replacement));
        ClientPolicy saved = store.clientAccess.value().get(existing.clientId());
        assertEquals(replacement, saved.credentials());
        assertEquals(existing.status(), saved.status());
        assertEquals(existing.allowedApps(), saved.allowedApps());
        assertEquals(0, store.publishCount);
        assertTrue(store.clientAccess.value().containsKey(new ClientId("other-client")));
    }

    @Test
    void shouldShareRevisionAndRejectStaleWritesAcrossBothClientOperations() {
        var existing = new ClientPolicy(new ClientId("client-a"), Set.of(new AppId("app-a")), clientCredentials("original"), ClientStatus.ENABLED);
        TestStore store = clientStore(existing);
        String originalRevision = store.clientAccess.revision();
        ClientPolicy disabled = existing.withAllowedApps(existing.allowedApps(), ClientStatus.DISABLED);
        clientAccessService(store).execute(new SaveClientAccessPolicyCommand(originalRevision, disabled));
        ApplicationException credentialConflict = assertThrows(ApplicationException.class,
                () -> clientCredentialService(store).save(new SaveClientCredentialPolicyCommand(originalRevision,
                        existing.clientId(), clientCredentials("replacement"))));
        assertEquals(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT.errorCode(), credentialConflict.getErrorCode());
        String accessRevision = store.clientAccess.revision();
        clientCredentialService(store).save(new SaveClientCredentialPolicyCommand(accessRevision,
                existing.clientId(), clientCredentials("replacement")));
        ApplicationException accessConflict = assertThrows(ApplicationException.class,
                () -> clientAccessService(store).execute(new SaveClientAccessPolicyCommand(accessRevision, existing)));
        assertEquals(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT.errorCode(), accessConflict.getErrorCode());
        assertEquals(ClientStatus.DISABLED, store.clientAccess.value().get(existing.clientId()).status());
        assertEquals(clientCredentials("replacement"), store.clientAccess.value().get(existing.clientId()).credentials());
        assertEquals(2, store.clientAccess.writeCount);
    }

    @Test
    void shouldRejectCredentialUpdateForMissingClientWithoutWriting() {
        var store = new TestStore(snapshot(1, true), content(true));
        assertThrows(DomainException.class, () -> clientCredentialService(store).save(
                new SaveClientCredentialPolicyCommand(store.clientAccess.revision(), new ClientId("client-a"), clientCredentials("active"))));
        assertEquals(0, store.clientAccess.writeCount);
        assertTrue(store.clientAccess.value().isEmpty());
    }

    @Test
    void shouldBlockBothClientOperationsDuringRollbackBeforeLoadingDefinition() {
        var existing = new ClientPolicy(new ClientId("client-a"), Set.of(new AppId("app-a")));
        TestStore store = clientStore(existing);
        store.active = true;
        store.clientAccess.failOnLoad = true;
        ApplicationException accessFailure = assertThrows(ApplicationException.class,
                () -> clientAccessService(store).execute(new SaveClientAccessPolicyCommand(store.clientAccess.revision(), existing)));
        ApplicationException credentialFailure = assertThrows(ApplicationException.class,
                () -> clientCredentialService(store).save(new SaveClientCredentialPolicyCommand(store.clientAccess.revision(),
                        existing.clientId(), clientCredentials("active"))));
        assertEquals(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS.errorCode(), accessFailure.getErrorCode());
        assertEquals(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS.errorCode(), credentialFailure.getErrorCode());
        assertEquals(0, store.clientAccess.readCount);
        assertEquals(0, store.clientAccess.writeCount);
    }

    @Test
    void shouldRejectCompareAndSetFailureForBothClientOperationsWithoutLosingExistingState() {
        var existing = new ClientPolicy(new ClientId("client-a"), Set.of(new AppId("app-a")), clientCredentials("original"), ClientStatus.ENABLED);
        TestStore store = clientStore(existing);
        store.clientAccess.rejectNextWrite = true;
        assertThrows(ApplicationException.class, () -> clientAccessService(store).execute(
                new SaveClientAccessPolicyCommand(store.clientAccess.revision(), existing.withAllowedApps(Set.of(), ClientStatus.DISABLED))));
        store.clientAccess.rejectNextWrite = true;
        assertThrows(ApplicationException.class, () -> clientCredentialService(store).save(
                new SaveClientCredentialPolicyCommand(store.clientAccess.revision(), existing.clientId(), clientCredentials("replacement"))));
        assertEquals(existing, store.clientAccess.value().get(existing.clientId()));
        assertEquals(0, store.clientAccess.writeCount);
    }

    private static ClientAccessPolicyCommandService clientAccessService(TestStore store) {
        return new ClientAccessPolicyCommandService(store.clientAccess, store, new PolicyPublishLock(store.lockExecutor));
    }

    private static ClientCredentialPolicyCommandService clientCredentialService(TestStore store) {
        return new ClientCredentialPolicyCommandService(store.clientAccess, store, new PolicyPublishLock(store.lockExecutor));
    }

    private static TestStore clientStore(ClientPolicy client) {
        IngressAccessContent source = content(true);
        var other = new ClientPolicy(new ClientId("other-client"), Set.of());
        return new TestStore(snapshot(1, true), new IngressAccessContent(source.endpointRegistry(), source.appPolicies(),
                Map.of(client.clientId(), client, other.clientId(), other), source.endpointPolicies(), source.policies()));
    }

    private static Credentials clientCredentials(String id) {
        return new Credentials(new Credential(new CredentialId(id), new ClientId("client-a"),
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, "secret-reference", Instant.EPOCH,
                Instant.parse("2099-01-01T00:00:00Z")), null);
    }

    private static PolicySnapshotCommandService snapshotService(TestStore store, PolicySnapshotHistory history) {
        return snapshotService(store, history, CLOCK);
    }

    private static PolicySnapshotCommandService snapshotService(TestStore store, PolicySnapshotHistory history, Clock clock) {
        PolicyDefinitionAccess definitions = new PolicyDefinitionAccess(
                store.endpoints, store.clients, store.clientAccess, store.routes, store.policies);
        com.cloud.gateway.domain.policy.header.HeaderRules rules =
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly();
        PolicySnapshotPublication publication = new PolicySnapshotPublication(store,
                new PolicySnapshotAssembler(PolicyCommandServicesTest::hash, rules), clock);
        PolicyPublishLock publishLock = new PolicyPublishLock(store.lockExecutor);
        return new PolicySnapshotCommandService(
                new PolicyPublicationCommandService(definitions, store, publication, store,
                        publishLock),
                new PolicyRollbackCommandService(definitions, store, publication, history, store,
                        PolicyCommandServicesTest::hash, publishLock, new PolicyRollbackLock(store.lockExecutor)));
    }

    private static String hash(IngressAccessContent content) {
        return (content.endpointPolicies().get(new EndpointId("LOGIN")).enabled() ? "enabled" : "disabled")
                + (content.appPolicies().isEmpty() ? "" : content.appPolicies().toString())
                + (content.policies().bundles().isEmpty() ? "" : content.policies().toString());
    }

    private static PolicyComposition configuredPolicies() {
        var id = new PolicyBundleId("standard");
        return new PolicyComposition(Map.of(id, new PolicyBundle(id, null,
                new TimeoutPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)),
                null, null, null)), List.of());
    }

    private static IngressAccessContent withPolicies(IngressAccessContent source, PolicyComposition policies) {
        return new IngressAccessContent(source.endpointRegistry(), source.appPolicies(), source.clients(),
                source.endpointPolicies(), policies);
    }

    @Test void shouldSaveOnlyPolicyConfigurationWithoutPublishing() {
        var store = new TestStore(snapshot(1, true), content(true));
        store.endpoints.failOnLoad = true;
        PolicyComposition policies = configuredPolicies();
        var service = new PolicyCompositionCommandService(store.policies, store, new PolicyPublishLock(store.lockExecutor), com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
        service.save(new SavePolicyCompositionCommand(store.policies.revision(), policies));
        assertEquals(policies, store.policies.value());
        assertEquals(0, store.publishCount);
        assertEquals(0, store.endpoints.readCount);
        assertThrows(ApplicationException.class, () -> service.save(new SavePolicyCompositionCommand("stale", policies)));
        store.active = true;
        assertThrows(ApplicationException.class, () -> service.save(new SavePolicyCompositionCommand(store.policies.revision(), policies)));
    }

    @Test void shouldPublishPolicyConfigurationOnlyChanges() {
        PolicyComposition policies = configuredPolicies();
        var store = new TestStore(snapshot(1, true), withPolicies(content(true), policies));
        snapshotService(store, version -> Optional.empty()).publish(new PublishPolicySnapshotCommand());
        assertEquals(policies, store.current().snapshot().content().policies());
        assertEquals(2, store.policies.readCount);
    }

    @Test void shouldRejectUnadmittedHeadersBeforeSavingOrPublishing() {
        var id = new PolicyBundleId("invalid-headers");
        var headers = new com.cloud.gateway.domain.policy.header.HeaderPolicy(Set.of(),
                Map.of("X-Unregistered", "value"), Set.of(), null);
        var policies = new PolicyComposition(Map.of(id,
                new PolicyBundle(id, null, null, null, null, headers)), List.of());
        var store = new TestStore(snapshot(1, true), content(true));
        var service = new PolicyCompositionCommandService(store.policies, store,
                new PolicyPublishLock(store.lockExecutor), com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
        assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                () -> service.save(new SavePolicyCompositionCommand(store.policies.revision(), policies)));
        assertTrue(store.policies.value().bundles().isEmpty());
        var publicationStore = new TestStore(snapshot(1, true), withPolicies(content(true), policies));
        assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                () -> snapshotService(publicationStore, version -> Optional.empty()).publish(new PublishPolicySnapshotCommand()));
        assertEquals(0, publicationStore.publishCount);
    }

    @Test void shouldRejectPolicyConfigurationRevisionChangeDuringPublish() {
        var store = new TestStore(snapshot(1, true), withPolicies(content(true), configuredPolicies()));
        store.policies.changeRevisionOnSecondRead = true;
        assertThrows(ApplicationException.class,
                () -> snapshotService(store, version -> Optional.empty()).publish(new PublishPolicySnapshotCommand()));
        assertEquals(0, store.publishCount);
    }

    @Test void shouldResumeRollbackWhenPolicyConfigurationRestoreFails() {
        IngressAccessContent targetContent = withPolicies(content(true), configuredPolicies());
        var target = new IngressAccessSnapshot(1, targetContent, hash(targetContent), CLOCK.instant());
        var store = new TestStore(snapshot(2, false), content(false));
        store.policies.rejectNextWrite = true;
        PolicySnapshotCommandService service = snapshotService(store, version -> Optional.of(target));
        assertThrows(ApplicationException.class, () -> service.rollback(new RollbackPolicySnapshotCommand(1)));
        assertTrue(store.active);
        assertEquals(0, store.publishCount);
        service.rollback(new RollbackPolicySnapshotCommand(1));
        assertEquals(targetContent.policies(), store.current().snapshot().content().policies());
        assertFalse(store.active);
        assertEquals(1, store.routes.writeCount);
    }

    private static IngressAccessSnapshot snapshot(long version, boolean enabled) {
        return new IngressAccessSnapshot(version, content(enabled), enabled ? "enabled" : "disabled",
                Instant.parse("2026-08-24T00:00:00Z").plusSeconds(version));
    }

    private static IngressAccessContent content(boolean enabled) {
        return content(enabled, Map.of());
    }

    private static IngressAccessContent content(
            boolean enabled,
            Map<AppId, AppPolicy> apps
    ) {
        EndpointRegistry catalog = new EndpointRegistry(List.of(new Endpoint(new EndpointId("LOGIN"),
                "service", List.of(new EndpointMatcher("POST", "/login")), "service")));
        return new IngressAccessContent(catalog, apps, Map.of(),
                Map.of(new EndpointId("LOGIN"), route(enabled)));
    }

    private static EndpointPolicy route(boolean enabled) {
        return new EndpointPolicy(new EndpointId("LOGIN"), ClientVerificationRequirement.FORBIDDEN, ClientVerificationMode.IGNORE, SubjectAuthenticationRequirement.IGNORE, Set.of(), Set.of(), CredentialForwarding.stripAll(), enabled ? EndpointStatus.ENABLED : EndpointStatus.DISABLED);
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

    private static final class EndpointDefinitions extends TestDefinition<EndpointRegistry>
            implements WritableEndpointRegistryDefinitionRepository {
        EndpointDefinitions(EndpointRegistry value) { super(value); }
    }

    private static final class ClientDefinitions extends TestDefinition<Map<AppId, AppPolicy>>
            implements WritableAppPolicyDefinitionRepository {
        ClientDefinitions(Map<AppId, AppPolicy> value) { super(new LinkedHashMap<>(value)); }
    }

    private static final class ClientAccessDefinitions
            extends TestDefinition<Map<ClientId, ClientPolicy>>
            implements WritableClientPolicyDefinitionRepository {
        ClientAccessDefinitions(Map<ClientId, ClientPolicy> value) {
            super(new LinkedHashMap<>(value));
        }
    }

    private static final class RouteDefinitions extends TestDefinition<Map<EndpointId, EndpointPolicy>>
            implements WritableEndpointAccessPolicyDefinitionRepository {
        RouteDefinitions(Map<EndpointId, EndpointPolicy> value) { super(new LinkedHashMap<>(value)); }
    }

    private static final class ConfigurationDefinitions extends TestDefinition<PolicyComposition>
            implements WritablePolicyCompositionDefinitionRepository {
        ConfigurationDefinitions(PolicyComposition value) { super(value); }
    }

    private static final class TestStore implements PolicySnapshotProvider, PolicySnapshotPublisher,
            PolicyRollbackState {
        private VersionedPolicySnapshot snapshot;
        private final EndpointDefinitions endpoints;
        private final ClientDefinitions clients;
        private final ClientAccessDefinitions clientAccess;
        private final RouteDefinitions routes;
        private final ConfigurationDefinitions policies;
        private int publishCount;
        private boolean active;
        private boolean failClear;
        private final RecordingLockExecutor lockExecutor = new RecordingLockExecutor();

        private TestStore(IngressAccessSnapshot snapshot, IngressAccessContent definitions) {
            this.snapshot = new VersionedPolicySnapshot(snapshot, "snapshot-1");
            endpoints = new EndpointDefinitions(definitions.endpointRegistry());
            clients = new ClientDefinitions(definitions.appPolicies());
            clientAccess = new ClientAccessDefinitions(definitions.clients());
            routes = new RouteDefinitions(definitions.endpointPolicies());
            policies = new ConfigurationDefinitions(definitions.policies());
        }

        @Override public VersionedPolicySnapshot current() { return snapshot; }

        @Override public boolean compareAndSet(String expectedSourceRevision, IngressAccessSnapshot next) {
            if (!snapshot.sourceRevision().equals(expectedSourceRevision)) {
                return false;
            }
            snapshot = new VersionedPolicySnapshot(next, "snapshot-" + next.version());
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
