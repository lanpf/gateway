package com.cloud.gateway.infrastructure.persistence.nacos;

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
import com.cloud.gateway.api.enums.AuthorizationHeaderPolicyApiEnum;
import com.cloud.gateway.api.enums.ClientVerificationRequirementApiEnum;
import com.cloud.gateway.api.enums.ClientStatusApiEnum;
import com.cloud.gateway.api.enums.AppStatusApiEnum;
import com.cloud.gateway.api.enums.CookieForwardingModeApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;
import com.cloud.gateway.api.enums.SubjectAuthenticationRequirementApiEnum;
import com.cloud.gateway.api.facade.IngressAccessAdminCommandFacade;
import com.cloud.gateway.api.facade.policy.CredentialForwardingApiCommand;
import com.cloud.gateway.api.facade.SnapshotReleaseApiCommandOutput;
import com.cloud.gateway.api.facade.ReleaseSnapshotApiCommand;
import com.cloud.gateway.api.facade.SaveClientApiCommand;
import com.cloud.gateway.api.facade.SaveAppApiCommand;
import com.cloud.gateway.api.facade.SaveEndpointApiCommand;
import com.cloud.gateway.application.access.editing.ClientAccessCommandService;
import com.cloud.gateway.application.access.editing.ClientCredentialCommandService;
import com.cloud.gateway.application.access.editing.AppAccessCommandService;
import com.cloud.gateway.application.access.AccessPublishLock;
import com.cloud.gateway.application.access.publication.PolicyConfigCommandService;
import com.cloud.gateway.application.access.publication.AccessRollbackLock;
import com.cloud.gateway.application.access.publication.AccessRollbackState;
import com.cloud.gateway.application.access.publication.AccessSnapshotCommandService;
import com.cloud.gateway.application.access.publication.AccessDefinitions;
import com.cloud.gateway.application.access.publication.AccessSnapshotPublication;
import com.cloud.gateway.application.access.publication.AccessPublicationCommandService;
import com.cloud.gateway.application.access.publication.AccessRollbackCommandService;
import com.cloud.gateway.application.access.publication.AccessSnapshotHasher;
import com.cloud.gateway.application.access.publication.AccessSnapshotHistory;
import com.cloud.gateway.application.access.editing.EndpointAccessCommandService;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
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
import com.cloud.gateway.interfaces.facade.DefaultIngressAccessAdminCommandFacade;
import com.cloud.gateway.interfaces.facade.mapper.DefaultPolicyConfigApiMapper;
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
import java.util.Map;
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
    private com.cloud.gateway.application.access.publication.AccessSnapshotHistory snapshotHistory;
    private NacosPolicyProperties policyProperties;
    private StoredPolicyDefinitionCodec definitionCodec;
    private StoredAccessSnapshotCodec snapshotCodec;
    private IngressAccessAdminCommandFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        String serverAddress = NACOS.getHost() + ":" + hostHttpPort;
        configService = NacosFactory.createConfigService(serverAddress);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        StoredAccessSnapshotMapper snapshotMapper = new StoredAccessSnapshotMapper();
        com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher = new CanonicalAccessSnapshotHasher(
                new CanonicalPolicyContentMapper(), objectMapper);
        definitionCodec = new StoredPolicyDefinitionCodec(snapshotMapper, objectMapper, com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
        snapshotCodec = new StoredAccessSnapshotCodec(snapshotMapper, hasher, objectMapper, com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());

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
        NacosEndpointAccessDefinitionRepository endpointAccessRepository =
                new NacosEndpointAccessDefinitionRepository(operations, policyProperties, definitionCodec);

        NacosPolicyConfigDefinitionRepository policyConfigurationRepository =
                new NacosPolicyConfigDefinitionRepository(operations, policyProperties, definitionCodec);

        com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState = new TestAccessRollbackState();
        LockExecutor lockExecutor = new ImmediateLockExecutor();
        AccessPublishLock publishLock = new AccessPublishLock(lockExecutor);
        com.cloud.gateway.application.access.publication.AccessRollbackLock rollbackLock = new com.cloud.gateway.application.access.publication.AccessRollbackLock(lockExecutor);
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

        com.cloud.gateway.application.access.publication.AccessDefinitions definitions = new com.cloud.gateway.application.access.publication.AccessDefinitions(endpointRepository, appRepository,
                clientAccessRepository, endpointAccessRepository, policyConfigurationRepository);
        com.cloud.gateway.domain.policy.header.HeaderRules headerRules =
                com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly();
        com.cloud.gateway.application.access.publication.AccessSnapshotPublication publication = new com.cloud.gateway.application.access.publication.AccessSnapshotPublication(snapshotStore,
                new com.cloud.gateway.application.access.com.cloud.gateway.application.access.publication.AccessSnapshotAssembler(hasher, headerRules), Clock.systemUTC());
        com.cloud.gateway.application.access.publication.AccessSnapshotCommandService snapshotService = new com.cloud.gateway.application.access.publication.AccessSnapshotCommandService(
                new com.cloud.gateway.application.access.publication.AccessPublicationCommandService(definitions, snapshotStore, publication, rollbackState,
                        publishLock),
                new com.cloud.gateway.application.access.publication.AccessRollbackCommandService(definitions, snapshotStore, publication, snapshotHistory,
                        rollbackState, hasher, publishLock, rollbackLock));
        facade = new DefaultIngressAccessAdminCommandFacade(
                new com.cloud.gateway.application.access.editing.AppAccessCommandService(appRepository, rollbackState, publishLock),
                new com.cloud.gateway.application.access.editing.ClientAccessCommandService(clientAccessRepository, rollbackState, publishLock),
                new com.cloud.gateway.application.access.editing.ClientCredentialCommandService(clientAccessRepository, rollbackState, publishLock),
                new com.cloud.gateway.application.access.editing.EndpointAccessCommandService(endpointAccessRepository, rollbackState, publishLock),
                snapshotService,
                org.mapstruct.factory.Mappers.getMapper(
                        com.cloud.gateway.interfaces.facade.mapper.mapstruct.GatewayPolicyApiMapStructMapper.class),
                new com.cloud.gateway.application.access.publication.PolicyConfigCommandService(policyConfigurationRepository, rollbackState, publishLock, com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly()),
                new DefaultPolicyConfigApiMapper());

    }

    @AfterEach
    void tearDown() throws Exception {
        if (snapshotStore != null) {
            snapshotStore.close();
        }
        if (configService != null) {
            removeAllPolicyConfigs();
            configService.shutDown();
        }
    }

    @Test
    void shouldPersistIndependentDefinitionsAndPublishTwoSnapshotsWithHistory() throws Exception {
        String initialApps = config(policyProperties.getAppDefinitionDataId());
        String initialClientAccess = config(policyProperties.getClientDefinitionDataId());
        String initialEndpointAccess = config(policyProperties.getRouteAccessDefinitionDataId());

        facade.saveApp(new SaveAppApiCommand(
                NacosAccessSnapshotStore.sourceRevision(initialApps),
                APP_ID,
                AppStatusApiEnum.ENABLED,
                "browser",
                Set.of("https://browser.example.com")));
        facade.saveClient(new SaveClientApiCommand(
                NacosAccessSnapshotStore.sourceRevision(initialClientAccess),
                CLIENT_ID,
                Set.of(APP_ID),
                ClientStatusApiEnum.ENABLED));
        // Nacos acknowledges CAS before its read-side view necessarily converges.
        // Wait for the saved definitions, not by retrying or weakening publication validation.
        await(this::areSavedIdentityDefinitionsReadable);

        String clientRevision = NacosAccessSnapshotStore.sourceRevision(
                config(policyProperties.getClientDefinitionDataId()));
        facade.saveClientCredentialPolicy(new com.cloud.gateway.api.policy.SaveClientCredentialPolicyApiCommand(
                clientRevision, CLIENT_ID,
                new com.cloud.gateway.api.policy.ClientCredentialApiCommand(
                        "admin-it-active", Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                        SignatureContractApiEnum.HTTP_REQUEST_SIGNATURE_V1,
                        "admin-it-secret-ref"), null));
        await(() -> {
            try {
                return definitionCodec.decodeClients(config(policyProperties.getClientDefinitionDataId()))
                        .get(new ClientId(CLIENT_ID)).credentials() != null;
            } catch (Exception exception) {
                return false;
            }
        });
        assertThrows(com.cloud.gateway.application.common.ApplicationException.class,
                () -> facade.saveClient(new SaveClientApiCommand(
                        clientRevision, CLIENT_ID, Set.of(), ClientStatusApiEnum.DISABLED)));

        Result<SnapshotReleaseApiCommandOutput> firstResult =
                facade.releaseSnapshot(new ReleaseSnapshotApiCommand());
        SnapshotReleaseApiCommandOutput firstOutput = firstResult.getData();
        assertNotNull(firstOutput);
        assertEquals(1, firstOutput.snapshotVersion());
        IngressAccessSnapshot firstSnapshot = awaitSnapshot(1);
        assertEquals(firstOutput.contentHash(), firstSnapshot.contentHash());
        assertEquals(1, firstSnapshot.version());
        assertTrue(firstSnapshot.content().appPolicies().containsKey(new AppId(APP_ID)));
        assertTrue(firstSnapshot.content().clients().containsKey(new ClientId(CLIENT_ID)));
        ClientAccess publishedClient = firstSnapshot.content().clients().get(new ClientId(CLIENT_ID));
        assertEquals(Set.of(new AppId(APP_ID)), publishedClient.allowedApps());
        assertEquals("admin-it-active", publishedClient.credentials().activeCredential().credentialId().value());
        assertTrue(firstSnapshot.content().endpointPolicies().isEmpty());

        facade.saveEndpoint(new SaveEndpointApiCommand(
                NacosAccessSnapshotStore.sourceRevision(initialEndpointAccess),
                ENDPOINT_ID,
                ClientVerificationModeApiEnum.IGNORE,
                ClientVerificationRequirementApiEnum.FORBIDDEN,
                SubjectAuthenticationRequirementApiEnum.IGNORE,
                Set.of(),
                Set.of(),
                new CredentialForwardingApiCommand(
                        AuthorizationHeaderPolicyApiEnum.STRIP,
                        CookieForwardingModeApiEnum.STRIP_ALL,
                        Set.of()),
                com.cloud.gateway.api.enums.EndpointStatusApiEnum.ENABLED));
        await(this::isRouteDefinitionReadable);

        Result<SnapshotReleaseApiCommandOutput> secondResult =
                facade.releaseSnapshot(new ReleaseSnapshotApiCommand());
        SnapshotReleaseApiCommandOutput secondOutput = secondResult.getData();
        assertNotNull(secondOutput);
        assertEquals(2, secondOutput.snapshotVersion());
        assertNotEquals(firstOutput.contentHash(), secondOutput.contentHash());

        IngressAccessSnapshot currentSnapshot = awaitSnapshot(2);
        assertAllDefinitionDataIds();
        assertEquals(secondOutput.contentHash(), currentSnapshot.contentHash());
        assertEquals(2, currentSnapshot.version());
        assertTrue(currentSnapshot.content().endpointPolicies().get(new EndpointId(ENDPOINT_ID)).enabled());

        await(() -> {
            try {
                return snapshotHistory.find(1).isPresent();
            } catch (InfrastructureException ignored) {
                return false;
            }
        });
        IngressAccessSnapshot historicalSnapshot = snapshotHistory.find(1).orElseThrow();
        assertEquals(firstOutput.contentHash(), historicalSnapshot.contentHash());
        assertTrue(historicalSnapshot.content().endpointPolicies().isEmpty());

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
        Path marker = temporaryDirectory.resolve("nacos-policy-administration-observation.active");
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
        assertTrue(definitionCodec.decodeEndpointRegistry(
                config(policyProperties.getEndpointDefinitionDataId())).definitions().containsKey(new EndpointId(ENDPOINT_ID)));
        assertTrue(definitionCodec.decodeApps(
                config(policyProperties.getAppDefinitionDataId())).containsKey(new AppId(APP_ID)));
        assertTrue(definitionCodec.decodeClients(
                config(policyProperties.getClientDefinitionDataId())).get(new ClientId(CLIENT_ID))
                .allowedApps().contains(new AppId(APP_ID)));
        assertEquals("admin-it-active", definitionCodec.decodeClients(
                config(policyProperties.getClientDefinitionDataId())).get(new ClientId(CLIENT_ID))
                .credentials().activeCredential().credentialId().value());
        assertTrue(definitionCodec.decodeEndpointAccess(
                config(policyProperties.getRouteAccessDefinitionDataId())).containsKey(new EndpointId(ENDPOINT_ID)));
        assertEquals(PolicyConfig.empty(), definitionCodec.decodePolicyConfiguration(
                config(policyProperties.getPolicyConfigurationDefinitionDataId())));
    }

    private IngressAccessSnapshot awaitSnapshot(long expectedVersion) throws Exception {
        await(() -> isSnapshotVersionReadable(expectedVersion));
        return snapshotCodec.decodeSnapshot(config(policyProperties.getDataId()));
    }

    private void seedDefinitionsAndBootstrapSnapshot(com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher) throws Exception {
        EndpointRegistry endpointRegistry = endpointRegistry();
        String endpoints = definitionCodec.encodeEndpointRegistry(endpointRegistry);
        String apps = definitionCodec.encodeApps(Map.of());
        String clientAccess = definitionCodec.encodeClients(Map.of());
        String endpointAccess = definitionCodec.encodeEndpointAccess(Map.of());
        PolicyConfig policies = PolicyConfig.empty();
        publish(policyProperties.getEndpointDefinitionDataId(), endpoints);
        publish(policyProperties.getAppDefinitionDataId(), apps);
        publish(policyProperties.getClientDefinitionDataId(), clientAccess);
        publish(policyProperties.getRouteAccessDefinitionDataId(), endpointAccess);
        publish(policyProperties.getPolicyConfigurationDefinitionDataId(),
                definitionCodec.encodePolicyConfiguration(policies));

        IngressAccessContent content = new IngressAccessContent(
                endpointRegistry, Map.of(), Map.of(), Map.of(), policies);
        IngressAccessSnapshot bootstrap = new IngressAccessSnapshot(
                0, content, hasher.hash(content), Instant.parse("2026-01-01T00:00:00Z"));
        publish(policyProperties.getDataId(), snapshotCodec.encodeSnapshot(bootstrap));
    }

    private EndpointRegistry endpointRegistry() {
        return new EndpointRegistry(List.of(new Endpoint(
                new EndpointId(ENDPOINT_ID),
                "user-auth",
                List.of(new EndpointMatcher("POST", "/api/user-auth/login")),
                "user-auth")));
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
        return isSnapshotVersionReadable(0);
    }

    private boolean isSnapshotVersionReadable(long expectedVersion) {
        try {
            String content = configService.getConfig(
                    policyProperties.getDataId(), GROUP, TimeUnit.SECONDS.toMillis(10));
            return StringUtils.hasText(content)
                    && snapshotCodec.decodeSnapshot(content).version() == expectedVersion;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean areSavedIdentityDefinitionsReadable() {
        try {
            return definitionCodec.decodeApps(config(policyProperties.getAppDefinitionDataId()))
                    .containsKey(new AppId(APP_ID))
                    && definitionCodec.decodeClients(config(policyProperties.getClientDefinitionDataId()))
                    .containsKey(new ClientId(CLIENT_ID));
        } catch (Exception unreadable) {
            return false;
        }
    }

    private boolean isRouteDefinitionReadable() {
        try {
            String content = configService.getConfig(
                    policyProperties.getRouteAccessDefinitionDataId(), GROUP, TimeUnit.SECONDS.toMillis(10));
            return StringUtils.hasText(content)
                    && definitionCodec.decodeEndpointAccess(content).containsKey(new EndpointId(ENDPOINT_ID));
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

    private void removeAllPolicyConfigs() throws Exception {
        configService.removeConfig(policyProperties.getEndpointDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getAppDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getClientDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getRouteAccessDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getPolicyConfigurationDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getDataId(), GROUP);
    }

    private static final class ImmediateLockExecutor implements LockExecutor {
        @Override
        public <T> Optional<T> execute(LockContext context, Callable<T> callable) throws Exception {
            return Optional.ofNullable(callable.call());
        }
    }

    private static final class TestAccessRollbackState implements com.cloud.gateway.application.access.publication.AccessRollbackState {
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
                throw new AssertionError("Timed out waiting for Nacos policy history");
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
