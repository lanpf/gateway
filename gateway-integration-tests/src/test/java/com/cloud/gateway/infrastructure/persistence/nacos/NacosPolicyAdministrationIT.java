package com.cloud.gateway.infrastructure.persistence.nacos;

import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;
import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.DefaultCanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.cloud.framework.core.Result;
import com.cloud.framework.lock.LockContext;
import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.api.enums.ClientVerificationRequirementApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;
import com.cloud.gateway.api.enums.SubjectAuthenticationRequirementApiEnum;
import com.cloud.gateway.api.facade.admin.definition.AccessDefinitionCommandFacade;
import com.cloud.gateway.api.facade.admin.definition.SaveAppApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveClientApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveClientCredentialApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveEndpointApiCommand;
import com.cloud.gateway.api.facade.admin.snapshot.AccessSnapshotCommandFacade;
import com.cloud.gateway.api.facade.admin.snapshot.ReleaseSnapshotApiCommand;
import com.cloud.gateway.api.facade.admin.snapshot.SnapshotUpdateApiOutput;
import com.cloud.gateway.application.configuration.editing.ClientAccessCommandService;
import com.cloud.gateway.application.configuration.editing.ClientCredentialCommandService;
import com.cloud.gateway.application.configuration.editing.AppAccessCommandService;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodAccessSnapshotStore;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyHistoryProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicyHistorySourceAdapter;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosAccessSnapshotHistory;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.DefaultNacosConfigOperations;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import com.cloud.gateway.interfaces.facade.admin.definition.DefaultAccessDefinitionCommandFacade;
import com.cloud.gateway.interfaces.facade.admin.snapshot.DefaultAccessSnapshotCommandFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.StringUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.web.client.RestClient;

@Testcontainers(disabledWithoutDocker = true)
class NacosPolicyAdministrationIT {
    private static final String MANUAL_OBSERVATION_SECONDS_PROPERTY =
            "gateway.it.manual-observation-seconds";
    private static final String GROUP = "GATEWAY";
    private static final String ENDPOINT_ID = "USER_AUTH.LOGIN";
    private static final String APP_ID = "web-client";
    private static final String CLIENT_ID = "WEB";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static int hostHttpPort;
    private static FixedPortNacosContainer NACOS;

    @BeforeAll
    static void startNacosContainer() {
        hostHttpPort = availablePortFamily();
        NACOS = new FixedPortNacosContainer(hostHttpPort);
        NACOS.start();
    }

    @AfterAll
    static void stopNacosContainer() {
        if (NACOS != null) {
            NACOS.stop();
        }
    }

    @TempDir Path temporaryDirectory;

    private ConfigService configService;
    private NacosAccessSnapshotStore snapshotStore;
    private com.cloud.gateway.application.configuration.publication.AccessSnapshotHistory snapshotHistory;
    private NacosPolicyProperties policyProperties;
    private StoredPolicyDefinitionCodec definitionCodec;
    private StoredAccessSnapshotCodec snapshotCodec;
    private AccessDefinitionCommandFacade definitionFacade;
    private AccessSnapshotCommandFacade snapshotFacade;

    @BeforeEach
    void setUp() throws Exception {
        String serverAddress = NACOS.getHost() + ":" + hostHttpPort;
        configService = NacosFactory.createConfigService(serverAddress);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        StoredAccessSnapshotMapper snapshotMapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))));
        com.cloud.gateway.application.configuration.publication.AccessSnapshotHasher hasher = new CanonicalAccessSnapshotHasher(
                new DefaultCanonicalPolicyContentMapper(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))), objectMapper);
        definitionCodec = new StoredPolicyDefinitionCodec(snapshotMapper, objectMapper, HeaderAdmissionRules.registeredOnly());
        snapshotCodec = new StoredAccessSnapshotCodec(snapshotMapper, hasher, objectMapper, HeaderAdmissionRules.registeredOnly());

        policyProperties = policyProperties();
        seedDefinitionsAndBootstrapSnapshot(hasher);
        await(this::isBootstrapSnapshotReadable);

        NacosConfigOperations operations = new DefaultNacosConfigOperations(configService);
        snapshotStore = new NacosAccessSnapshotStore(
                operations,
                policyProperties,
                snapshotCodec,
                new FileLastKnownGoodAccessSnapshotStore(policyProperties.getLastKnownGoodLocation()));
        snapshotStore.initialize();

        NacosEndpointAccessDefinitionRepository endpointRepository =
                new NacosEndpointAccessDefinitionRepository(operations, policyProperties, definitionCodec);
        NacosAppAccessDefinitionRepository appRepository =
                new NacosAppAccessDefinitionRepository(operations, policyProperties, definitionCodec);
        NacosClientAccessDefinitionRepository clientAccessRepository =
                new NacosClientAccessDefinitionRepository(operations, policyProperties, definitionCodec);

        NacosCapabilityCompositionDefinitionRepository policyConfigurationRepository =
                new NacosCapabilityCompositionDefinitionRepository(operations, policyProperties, definitionCodec);

        com.cloud.gateway.application.configuration.publication.AccessRollbackState rollbackState = new TestAccessRollbackState();
        LockExecutor lockExecutor = new ImmediateLockExecutor();
        AccessPublishLock publishLock = new AccessPublishLock(lockExecutor);
        com.cloud.gateway.application.configuration.publication.AccessRollbackLock rollbackLock = new com.cloud.gateway.application.configuration.publication.AccessRollbackLock(lockExecutor);
        NacosConfigProperties nacosProperties = new NacosConfigProperties();
        nacosProperties.setServerAddr(serverAddress);
        NacosPolicyHistoryProperties historyProperties = new NacosPolicyHistoryProperties();
        snapshotHistory = new NacosAccessSnapshotHistory(
                new NacosPolicyHistorySourceAdapter(
                        RestClient.builder().baseUrl("http://" + serverAddress + "/nacos").build(),
                        policyProperties,
                        historyProperties,
                        nacosProperties,
                        Clock.systemUTC()),
                snapshotCodec);

        com.cloud.gateway.application.configuration.publication.AccessDefinitions definitions = new com.cloud.gateway.application.configuration.publication.AccessDefinitions(endpointRepository, appRepository,
                clientAccessRepository, policyConfigurationRepository);
        HeaderAdmissionRules headerRules =
                HeaderAdmissionRules.registeredOnly();
        com.cloud.gateway.application.configuration.publication.AccessSnapshotPublication publication = new com.cloud.gateway.application.configuration.publication.AccessSnapshotPublication(snapshotStore,
                new com.cloud.gateway.application.configuration.publication.AccessSnapshotAssembler(hasher, headerRules, com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry()), Clock.systemUTC());
        com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService snapshotService = new com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService(
                new com.cloud.gateway.application.configuration.publication.AccessPublicationCommandService(definitions, snapshotStore, publication, rollbackState,
                        publishLock),
                new com.cloud.gateway.application.configuration.publication.AccessRollbackCommandService(definitions, snapshotStore, publication, snapshotHistory,
                        rollbackState, hasher, publishLock, rollbackLock));
        definitionFacade = new DefaultAccessDefinitionCommandFacade(
                new AppAccessCommandService(appRepository, rollbackState, publishLock),
                new ClientAccessCommandService(clientAccessRepository, rollbackState, publishLock),
                new ClientCredentialCommandService(clientAccessRepository, rollbackState, publishLock),
                new com.cloud.gateway.application.configuration.editing.EndpointAccessCommandService(endpointRepository, rollbackState, publishLock),
                new com.cloud.gateway.interfaces.facade.admin.definition.mapstruct.AccessDefinitionApiMapStructMapperImpl(),
                new com.cloud.gateway.application.configuration.editing.CapabilityCompositionCommandService(
                        policyConfigurationRepository, rollbackState, publishLock, headerRules,
                        com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry()),
                new com.cloud.gateway.interfaces.facade.admin.definition.mapstruct.CapabilityCompositionApiMapStructMapperImpl(
                        com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(
                                com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(),
                                new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(),
                                new com.fasterxml.jackson.databind.ObjectMapper())));
        snapshotFacade = new DefaultAccessSnapshotCommandFacade(snapshotService,
                new com.cloud.gateway.interfaces.facade.admin.snapshot.mapstruct.AccessSnapshotApiMapStructMapperImpl());

    }

    @AfterEach
    void tearDown() throws Exception {
        if (snapshotStore != null) {
            snapshotStore.close();
        }
        if (configService != null) {
            removeAllCapabilityCompositions();
            configService.shutDown();
        }
    }

    @Test
    void shouldPersistIndependentDefinitionsAndPublishTwoSnapshotsWithHistory() throws Exception {
        String initialApps = config(policyProperties.getAppDefinitionDataId());
        String initialClientAccess = config(policyProperties.getClientDefinitionDataId());
        String initialEndpoints = config(policyProperties.getEndpointDefinitionDataId());

        definitionFacade.saveApp(new SaveAppApiCommand(
                NacosAccessSnapshotStore.sourceRevision(initialApps),
                APP_ID,
                "browser",
                Set.of("https://browser.example.com")));
        definitionFacade.saveClient(new SaveClientApiCommand(
                NacosAccessSnapshotStore.sourceRevision(initialClientAccess),
                CLIENT_ID,
                Set.of(APP_ID)));
        // Nacos acknowledges CAS before its read-side view necessarily converges.
        // Wait for the saved definitions, not by retrying or weakening publication validation.
        await(this::areSavedIdentityDefinitionsReadable);

        String clientRevision = NacosAccessSnapshotStore.sourceRevision(
                config(policyProperties.getClientDefinitionDataId()));
        definitionFacade.saveClientCredential(new SaveClientCredentialApiCommand(
                clientRevision, CLIENT_ID,
                new SaveClientCredentialApiCommand.CredentialApiCommand(
                        "admin-it-active", "admin-it-secret-ref",
                        SignatureContractApiEnum.HTTP_REQUEST_SIGNATURE_V1,
                        Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)),
                null));
        await(() -> {
            try {
                return definitionCodec.decodeClients(config(policyProperties.getClientDefinitionDataId()))
                        .find(new ClientId(CLIENT_ID)).orElseThrow().access().credentials() != null;
            } catch (Exception exception) {
                return false;
            }
        });
        assertThrows(com.cloud.gateway.application.common.ApplicationException.class,
                () -> definitionFacade.saveClient(new SaveClientApiCommand(
                        clientRevision, CLIENT_ID, Set.of())));

        Result<SnapshotUpdateApiOutput> firstResult =
                snapshotFacade.release(new ReleaseSnapshotApiCommand());
        SnapshotUpdateApiOutput firstOutput = firstResult.getData();
        assertNotNull(firstOutput);
        assertEquals(2L, firstOutput.snapshotVersion());
        IngressSnapshot firstSnapshot = awaitSnapshot(2);
        assertEquals(firstOutput.contentHash(), firstSnapshot.contentHash());
        assertEquals(2L, firstSnapshot.version().value());
        assertTrue(firstSnapshot.content().apps().find(new AppId(APP_ID)).isPresent());
        Client publishedClient = firstSnapshot.content().clients()
                .find(new ClientId(CLIENT_ID)).orElseThrow();
        assertEquals(Set.of(new AppId(APP_ID)), publishedClient.access().allowedApps());
        assertEquals("admin-it-active",
                publishedClient.access().credentials().activeCredential().credentialId().value());
        // Endpoint access state is stored inline in the endpoint definition, never in a separate document.
        assertEquals(ClientVerificationRequirement.OPTIONAL, firstSnapshot.content().endpoints()
                .find(new EndpointId(ENDPOINT_ID)).orElseThrow().access().clientVerificationRequirement());

        definitionFacade.saveEndpoint(new SaveEndpointApiCommand(
                NacosAccessSnapshotStore.sourceRevision(initialEndpoints),
                ENDPOINT_ID,
                "user-auth",
                "user-auth",
                List.of(new SaveEndpointApiCommand.MatcherApiCommand("POST", "/api/user-auth/login")),
                Set.of(),
                ClientVerificationRequirementApiEnum.REQUIRED,
                SubjectAuthenticationRequirementApiEnum.REQUIRED,
                Set.of(),
                Set.of()));
        await(this::isEndpointDefinitionReadable);

        Result<SnapshotUpdateApiOutput> secondResult =
                snapshotFacade.release(new ReleaseSnapshotApiCommand());
        SnapshotUpdateApiOutput secondOutput = secondResult.getData();
        assertNotNull(secondOutput);
        assertEquals(3L, secondOutput.snapshotVersion());
        assertNotEquals(firstOutput.contentHash(), secondOutput.contentHash());

        IngressSnapshot currentSnapshot = awaitSnapshot(3);
        assertAllDefinitionDataIds();
        assertEquals(secondOutput.contentHash(), currentSnapshot.contentHash());
        assertEquals(3L, currentSnapshot.version().value());
        Endpoint currentEndpoint = currentSnapshot.content().endpoints()
                .find(new EndpointId(ENDPOINT_ID)).orElseThrow();
        assertTrue(currentEndpoint.enabled());
        assertEquals(ClientVerificationRequirement.REQUIRED,
                currentEndpoint.access().clientVerificationRequirement());

        await(() -> {
            try {
                return snapshotHistory.find(new SnapshotVersion(2)).isPresent();
            } catch (InfrastructureException ignored) {
                return false;
            }
        });
        IngressSnapshot historicalSnapshot = snapshotHistory.find(new SnapshotVersion(2)).orElseThrow();
        assertEquals(firstOutput.contentHash(), historicalSnapshot.contentHash());
        assertEquals(ClientVerificationRequirement.OPTIONAL, historicalSnapshot.content().endpoints()
                .find(new EndpointId(ENDPOINT_ID)).orElseThrow().access().clientVerificationRequirement());

        manualObservationPoint();
    }

    private void manualObservationPoint() throws Exception {
        URI consoleUri = URI.create("http://" + NACOS.getHost() + ":" + (hostHttpPort + 1) + "/");
        awaitConsole(consoleUri);
        System.out.println("Nacos console: " + consoleUri);
        // Set an IDE breakpoint on the next statement with "Suspend: All".
        long observationSeconds = Long.getLong(MANUAL_OBSERVATION_SECONDS_PROPERTY, 0L);
        if (observationSeconds <= 0) {
            return;
        }
        Path marker = temporaryDirectory.resolve("nacos-access-administration-observation.active");
        Files.writeString(marker, "Delete this file to finish manual observation early.\n");
        System.out.println("Nacos manual observation is active for at most " + observationSeconds + " seconds.");
        System.out.println("Delete this marker to finish early: " + marker);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(observationSeconds);
        while (Files.exists(marker) && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(250));
        }
        Files.deleteIfExists(marker);
    }

    private static void awaitConsole(URI consoleUri) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!isConsoleAccessible(consoleUri)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for Nacos console: " + consoleUri);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
        }
    }

    private static boolean isConsoleAccessible(URI consoleUri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(consoleUri)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            int statusCode = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return statusCode >= 200 && statusCode < 400;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void assertAllDefinitionDataIds() throws Exception {
        Endpoint storedEndpoint = definitionCodec.decodeEndpointRegistry(
                config(policyProperties.getEndpointDefinitionDataId()))
                .find(new EndpointId(ENDPOINT_ID)).orElseThrow();
        assertEquals(ClientVerificationRequirement.REQUIRED,
                storedEndpoint.access().clientVerificationRequirement());
        assertTrue(definitionCodec.decodeApps(
                config(policyProperties.getAppDefinitionDataId())).find(new AppId(APP_ID)).isPresent());
        assertTrue(definitionCodec.decodeClients(
                config(policyProperties.getClientDefinitionDataId())).find(new ClientId(CLIENT_ID))
                .orElseThrow().access().allowedApps().contains(new AppId(APP_ID)));
        assertEquals("admin-it-active", definitionCodec.decodeClients(
                config(policyProperties.getClientDefinitionDataId())).find(new ClientId(CLIENT_ID))
                .orElseThrow().access().credentials().activeCredential().credentialId().value());
        assertEquals(CapabilityComposition.empty(), definitionCodec.decodeCapabilityComposition(
                config(policyProperties.getPolicyConfigurationDefinitionDataId())));
    }

    private IngressSnapshot awaitSnapshot(long expectedVersion) throws Exception {
        await(() -> isSnapshotVersionReadable(expectedVersion));
        return snapshotCodec.decodeSnapshot(config(policyProperties.getDataId()));
    }

    private void seedDefinitionsAndBootstrapSnapshot(com.cloud.gateway.application.configuration.publication.AccessSnapshotHasher hasher) throws Exception {
        EndpointRegistry endpointRegistry = endpointRegistry();
        AppRegistry appRegistry = new AppRegistry(List.of());
        ClientRegistry clientRegistry = new ClientRegistry(List.of());
        CapabilityComposition policies = CapabilityComposition.empty();
        publish(policyProperties.getEndpointDefinitionDataId(),
                definitionCodec.encodeEndpointRegistry(endpointRegistry));
        publish(policyProperties.getAppDefinitionDataId(), definitionCodec.encodeApps(appRegistry));
        publish(policyProperties.getClientDefinitionDataId(), definitionCodec.encodeClients(clientRegistry));
        publish(policyProperties.getPolicyConfigurationDefinitionDataId(),
                definitionCodec.encodeCapabilityComposition(policies));

        IngressContent content = new IngressContent(
                appRegistry, clientRegistry, endpointRegistry, policies);
        IngressSnapshot bootstrap = new IngressSnapshot(
                new SnapshotVersion(1), content, hasher.hash(content),
                Instant.parse("2026-01-01T00:00:00Z"));
        publish(policyProperties.getDataId(), snapshotCodec.encodeSnapshot(bootstrap));
    }

    private EndpointRegistry endpointRegistry() {
        return new EndpointRegistry(List.of(new Endpoint(
                new EndpointId(ENDPOINT_ID),
                new ServiceId("user-auth"),
                new RouteId("user-auth"),
                List.of(new EndpointMatcher("POST", "/api/user-auth/login")),
                Set.of(),
                EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()))));
    }

    private NacosPolicyProperties policyProperties() {
        NacosPolicyProperties properties = new NacosPolicyProperties();
        properties.setGroup(GROUP);
        properties.setReadTimeout(Duration.ofSeconds(10));
        properties.setRecoveryInterval(Duration.ofMinutes(1));
        properties.setLastKnownGoodLocation(temporaryDirectory.resolve("last-known-good.yaml"));
        return properties;
    }

    private void publish(String dataId, String content) throws Exception {
        assertTrue(configService.publishConfig(dataId, GROUP, content, ConfigType.YAML.getType()));
    }

    private boolean isBootstrapSnapshotReadable() {
        return isSnapshotVersionReadable(1);
    }

    private boolean isSnapshotVersionReadable(long expectedVersion) {
        try {
            String content = configService.getConfig(
                    policyProperties.getDataId(), GROUP, TimeUnit.SECONDS.toMillis(10));
            return StringUtils.hasText(content)
                    && snapshotCodec.decodeSnapshot(content).version().value() == expectedVersion;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean areSavedIdentityDefinitionsReadable() {
        try {
            return definitionCodec.decodeApps(config(policyProperties.getAppDefinitionDataId()))
                    .find(new AppId(APP_ID)).isPresent()
                    && definitionCodec.decodeClients(config(policyProperties.getClientDefinitionDataId()))
                    .find(new ClientId(CLIENT_ID)).isPresent();
        } catch (Exception unreadable) {
            return false;
        }
    }

    /** The endpoint save is visible once the merged definition carries the new access requirement. */
    private boolean isEndpointDefinitionReadable() {
        try {
            String content = configService.getConfig(
                    policyProperties.getEndpointDefinitionDataId(), GROUP, TimeUnit.SECONDS.toMillis(10));
            return StringUtils.hasText(content)
                    && definitionCodec.decodeEndpointRegistry(content).find(new EndpointId(ENDPOINT_ID))
                            .map(endpoint -> endpoint.access().clientVerificationRequirement()
                                    == ClientVerificationRequirement.REQUIRED)
                            .orElse(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String config(String dataId) throws Exception {
        String content = configService.getConfig(dataId, GROUP, TimeUnit.SECONDS.toMillis(10));
        assertNotNull(content, () -> "Missing Nacos dataId: " + dataId);
        assertTrue(StringUtils.hasText(content), () -> "Blank Nacos dataId: " + dataId);
        return content;
    }

    private void removeAllCapabilityCompositions() throws Exception {
        configService.removeConfig(policyProperties.getEndpointDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getAppDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getClientDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getPolicyConfigurationDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getDataId(), GROUP);
    }

    private static final class ImmediateLockExecutor implements LockExecutor {
        @Override
        public <T> Optional<T> execute(LockContext context, Callable<T> callable) throws Exception {
            return Optional.ofNullable(callable.call());
        }
    }

    private static final class TestAccessRollbackState implements com.cloud.gateway.application.configuration.publication.AccessRollbackState {
        private final AtomicBoolean active = new AtomicBoolean();

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void activate() {
            active.set(true);
        }

        @Override
        public void clear() {
            active.set(false);
        }
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for Nacos access history");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
        }
    }

    private static int availablePortFamily() {
        for (int attempt = 0; attempt < 100; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(20_000, 50_000);
            try (ServerSocket http = bind(candidate);
                    ServerSocket console = bind(candidate + 1);
                    ServerSocket grpcClient = bind(candidate + 1_000);
                    ServerSocket grpcServer = bind(candidate + 1_001)) {
                return candidate;
            } catch (Exception ignored) {
                // Try another port family.
            }
        }
        throw new IllegalStateException("Unable to reserve a Nacos HTTP/gRPC port family");
    }

    private static ServerSocket bind(int port) throws Exception {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(false);
        socket.bind(new InetSocketAddress(port));
        return socket;
    }

    private static final class FixedPortNacosContainer extends GenericContainer<FixedPortNacosContainer> {
        private FixedPortNacosContainer(int hostHttpPort) {
            super(DockerImageName.parse("nacos/nacos-server:v3.0.3"));
            addFixedExposedPort(hostHttpPort, 8848);
            addFixedExposedPort(hostHttpPort + 1, 8080);
            addFixedExposedPort(hostHttpPort + 1_000, 9848);
            addFixedExposedPort(hostHttpPort + 1_001, 9849);
            withEnv("MODE", "standalone");
            withEnv("NACOS_AUTH_TOKEN", "U2VjcmV0S2V5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5");
            withEnv("NACOS_AUTH_IDENTITY_KEY", "gateway-integration-test");
            withEnv("NACOS_AUTH_IDENTITY_VALUE", "gateway-integration-test-secret");
            withEnv("NACOS_AUTH_ENABLE", "false");
            withEnv("NACOS_AUTH_ADMIN_ENABLE", "false");
            withEnv("NACOS_AUTH_CONSOLE_ENABLE", "false");
            waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(
                    ".*Nacos started successfully.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));
        }
    }
}
