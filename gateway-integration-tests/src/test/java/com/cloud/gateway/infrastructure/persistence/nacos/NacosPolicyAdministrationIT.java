package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.cloud.framework.core.Result;
import com.cloud.framework.lock.LockContext;
import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.api.enums.AuthorizationHeaderPolicyApiEnum;
import com.cloud.gateway.api.enums.ChannelCredentialRequirementApiEnum;
import com.cloud.gateway.api.enums.ChannelRequirementApiEnum;
import com.cloud.gateway.api.enums.ClientAppStatusApiEnum;
import com.cloud.gateway.api.enums.CookieForwardingModeApiEnum;
import com.cloud.gateway.api.enums.SubjectRequirementApiEnum;
import com.cloud.gateway.api.facade.GatewayPolicyCommandFacade;
import com.cloud.gateway.api.policy.CredentialForwardingApiCommand;
import com.cloud.gateway.api.policy.PolicyPublicationApiCommandOutput;
import com.cloud.gateway.api.policy.PublishPolicySnapshotApiCommand;
import com.cloud.gateway.api.policy.SaveChannelAccessPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveClientAppPolicyApiCommand;
import com.cloud.gateway.api.policy.SaveRouteAccessPolicyApiCommand;
import com.cloud.gateway.application.policy.ChannelAccessPolicyCommandService;
import com.cloud.gateway.application.policy.ChannelCredentialPolicyCommandService;
import com.cloud.gateway.application.policy.ClientAppPolicyCommandService;
import com.cloud.gateway.application.policy.PolicyPublishLock;
import com.cloud.gateway.application.policy.PolicyRollbackLock;
import com.cloud.gateway.application.policy.PolicyRollbackState;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.application.policy.PolicySnapshotHistory;
import com.cloud.gateway.application.policy.RouteAccessPolicyCommandService;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicySnapshotHasher;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodPolicySnapshotStore;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyHistoryProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicyHistorySourceAdapter;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicySnapshotHistory;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.DefaultNacosConfigOperations;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import com.cloud.gateway.interfaces.facade.DefaultGatewayPolicyCommandFacade;
import com.cloud.gateway.interfaces.facade.mapper.GatewayPolicyApiMapper;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.StringUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.web.client.RestClient;

@Testcontainers(disabledWithoutDocker = true)
class NacosPolicyAdministrationIT {
    private static final String MANUAL_OBSERVATION_SECONDS_PROPERTY =
            "gateway.it.manual-observation-seconds";
    private static final String GROUP = "GATEWAY";
    private static final String ENDPOINT_ID = "USER_AUTH.LOGIN";
    private static final String CLIENT_APP_ID = "web-client";
    private static final String CHANNEL_CODE = "WEB";
    private static final int HOST_HTTP_PORT = availablePortFamily();
    private static final int HOST_CONSOLE_PORT = HOST_HTTP_PORT + 1;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Container
    private static final FixedPortNacosContainer NACOS = new FixedPortNacosContainer(HOST_HTTP_PORT);

    @TempDir Path temporaryDirectory;

    private ConfigService configService;
    private NacosPolicySnapshotStore snapshotStore;
    private PolicySnapshotHistory snapshotHistory;
    private NacosPolicyProperties policyProperties;
    private StoredPolicyDefinitionCodec definitionCodec;
    private StoredPolicySnapshotCodec snapshotCodec;
    private GatewayPolicyCommandFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        String serverAddress = NACOS.getHost() + ":" + HOST_HTTP_PORT;
        configService = NacosFactory.createConfigService(serverAddress);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        StoredPolicySnapshotMapper snapshotMapper = new StoredPolicySnapshotMapper();
        PolicySnapshotHasher hasher = new CanonicalPolicySnapshotHasher(
                new CanonicalPolicyContentMapper(), objectMapper);
        definitionCodec = new StoredPolicyDefinitionCodec(snapshotMapper, objectMapper);
        snapshotCodec = new StoredPolicySnapshotCodec(snapshotMapper, hasher, objectMapper);

        policyProperties = policyProperties();
        seedDefinitionsAndBootstrapSnapshot(hasher);
        await(this::isBootstrapSnapshotReadable);

        NacosConfigOperations operations = new DefaultNacosConfigOperations(configService);
        snapshotStore = new NacosPolicySnapshotStore(
                operations,
                policyProperties,
                snapshotCodec,
                new FileLastKnownGoodPolicySnapshotStore(policyProperties.getLastKnownGoodLocation()));
        snapshotStore.initialize();

        NacosEndpointCatalogDefinitionRepository endpointRepository =
                new NacosEndpointCatalogDefinitionRepository(operations, policyProperties, definitionCodec);
        NacosClientAppPolicyDefinitionRepository clientAppRepository =
                new NacosClientAppPolicyDefinitionRepository(operations, policyProperties, definitionCodec);
        NacosChannelAccessPolicyDefinitionRepository channelAccessRepository =
                new NacosChannelAccessPolicyDefinitionRepository(operations, policyProperties, definitionCodec);
        NacosChannelCredentialPolicyDefinitionRepository channelCredentialRepository =
                new NacosChannelCredentialPolicyDefinitionRepository(operations, policyProperties, definitionCodec);
        NacosRouteAccessPolicyDefinitionRepository routeAccessRepository =
                new NacosRouteAccessPolicyDefinitionRepository(operations, policyProperties, definitionCodec);

        PolicyRollbackState rollbackState = new TestPolicyRollbackState();
        LockExecutor lockExecutor = new ImmediateLockExecutor();
        PolicyPublishLock publishLock = new PolicyPublishLock(lockExecutor);
        PolicyRollbackLock rollbackLock = new PolicyRollbackLock(lockExecutor);
        NacosConfigProperties nacosProperties = new NacosConfigProperties();
        nacosProperties.setServerAddr(serverAddress);
        NacosPolicyHistoryProperties historyProperties = new NacosPolicyHistoryProperties();
        snapshotHistory = new NacosPolicySnapshotHistory(
                new NacosPolicyHistorySourceAdapter(
                        RestClient.builder().baseUrl("http://" + serverAddress + "/nacos").build(),
                        policyProperties,
                        historyProperties,
                        nacosProperties,
                        Clock.systemUTC()),
                snapshotCodec);

        PolicySnapshotCommandService snapshotService = new PolicySnapshotCommandService(
                endpointRepository,
                clientAppRepository,
                channelAccessRepository,
                channelCredentialRepository,
                routeAccessRepository,
                snapshotStore,
                snapshotStore,
                snapshotHistory,
                rollbackState,
                hasher,
                publishLock,
                rollbackLock,
                Clock.systemUTC());
        facade = new DefaultGatewayPolicyCommandFacade(
                new ClientAppPolicyCommandService(clientAppRepository, rollbackState, publishLock),
                new ChannelAccessPolicyCommandService(channelAccessRepository, rollbackState, publishLock),
                new ChannelCredentialPolicyCommandService(channelCredentialRepository, rollbackState, publishLock),
                new RouteAccessPolicyCommandService(routeAccessRepository, rollbackState, publishLock),
                snapshotService,
                new GatewayPolicyApiMapper() { });

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
        String initialClientApps = config(policyProperties.getClientAppDefinitionDataId());
        String initialChannelAccess = config(policyProperties.getChannelAccessDefinitionDataId());
        String initialRouteAccess = config(policyProperties.getRouteAccessDefinitionDataId());

        facade.saveClientAppPolicy(new SaveClientAppPolicyApiCommand(
                NacosPolicySnapshotStore.sourceRevision(initialClientApps),
                CLIENT_APP_ID,
                ClientAppStatusApiEnum.ENABLED,
                "browser"));
        facade.saveChannelAccessPolicy(new SaveChannelAccessPolicyApiCommand(
                NacosPolicySnapshotStore.sourceRevision(initialChannelAccess),
                CHANNEL_CODE,
                Set.of(CLIENT_APP_ID)));

        Result<PolicyPublicationApiCommandOutput> firstResult =
                facade.publishPolicySnapshot(new PublishPolicySnapshotApiCommand());
        PolicyPublicationApiCommandOutput firstOutput = firstResult.getData();
        assertNotNull(firstOutput);
        assertEquals(1, firstOutput.snapshotVersion());
        IngressPolicySnapshot firstSnapshot = awaitSnapshot(1);
        assertEquals(firstOutput.contentHash(), firstSnapshot.contentHash());
        assertEquals(1, firstSnapshot.snapshotVersion());
        assertTrue(firstSnapshot.content().clientAppPolicies().containsKey(new ClientAppId(CLIENT_APP_ID)));
        assertTrue(firstSnapshot.content().channelAccessPolicies().containsKey(new ChannelCode(CHANNEL_CODE)));
        assertTrue(firstSnapshot.content().routeAccessPolicies().isEmpty());

        facade.saveRouteAccessPolicy(new SaveRouteAccessPolicyApiCommand(
                NacosPolicySnapshotStore.sourceRevision(initialRouteAccess),
                ENDPOINT_ID,
                ChannelCredentialRequirementApiEnum.IGNORE,
                ChannelRequirementApiEnum.FORBIDDEN,
                SubjectRequirementApiEnum.IGNORE,
                Set.of(),
                Set.of(),
                new CredentialForwardingApiCommand(
                        AuthorizationHeaderPolicyApiEnum.STRIP,
                        CookieForwardingModeApiEnum.STRIP_ALL,
                        Set.of()),
                true));
        await(this::isRouteDefinitionReadable);

        Result<PolicyPublicationApiCommandOutput> secondResult =
                facade.publishPolicySnapshot(new PublishPolicySnapshotApiCommand());
        PolicyPublicationApiCommandOutput secondOutput = secondResult.getData();
        assertNotNull(secondOutput);
        assertEquals(2, secondOutput.snapshotVersion());
        assertNotEquals(firstOutput.contentHash(), secondOutput.contentHash());

        IngressPolicySnapshot currentSnapshot = awaitSnapshot(2);
        assertAllDefinitionDataIds();
        assertEquals(secondOutput.contentHash(), currentSnapshot.contentHash());
        assertEquals(2, currentSnapshot.snapshotVersion());
        assertTrue(currentSnapshot.content().routeAccessPolicies().get(new EndpointId(ENDPOINT_ID)).enabled());

        await(() -> snapshotHistory.find(1).isPresent());
        IngressPolicySnapshot historicalSnapshot = snapshotHistory.find(1).orElseThrow();
        assertEquals(firstOutput.contentHash(), historicalSnapshot.contentHash());
        assertTrue(historicalSnapshot.content().routeAccessPolicies().isEmpty());

        manualObservationPoint();
    }

    private void manualObservationPoint() throws Exception {
        URI consoleUri = URI.create("http://" + NACOS.getHost() + ":" + HOST_CONSOLE_PORT + "/");
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
        assertTrue(definitionCodec.decodeEndpointCatalog(
                config(policyProperties.getEndpointDefinitionDataId())).asMap().containsKey(new EndpointId(ENDPOINT_ID)));
        assertTrue(definitionCodec.decodeClientApps(
                config(policyProperties.getClientAppDefinitionDataId())).containsKey(new ClientAppId(CLIENT_APP_ID)));
        assertTrue(definitionCodec.decodeChannelAccess(
                config(policyProperties.getChannelAccessDefinitionDataId())).get(new ChannelCode(CHANNEL_CODE))
                .allowedClientAppIds().contains(new ClientAppId(CLIENT_APP_ID)));
        assertTrue(definitionCodec.decodeChannelCredentials(
                config(policyProperties.getChannelCredentialDefinitionDataId())).isEmpty());
        assertTrue(definitionCodec.decodeRouteAccess(
                config(policyProperties.getRouteAccessDefinitionDataId())).containsKey(new EndpointId(ENDPOINT_ID)));
    }

    private IngressPolicySnapshot awaitSnapshot(long expectedVersion) throws Exception {
        await(() -> isSnapshotVersionReadable(expectedVersion));
        return snapshotCodec.decodeSnapshot(config(policyProperties.getDataId()));
    }

    private void seedDefinitionsAndBootstrapSnapshot(PolicySnapshotHasher hasher) throws Exception {
        EndpointCatalog endpointCatalog = endpointCatalog();
        String endpoints = definitionCodec.encodeEndpointCatalog(endpointCatalog);
        String clientApps = definitionCodec.encodeClientApps(Map.of());
        String channelAccess = definitionCodec.encodeChannelAccess(Map.of());
        String channelCredentials = definitionCodec.encodeChannelCredentials(Map.of());
        String routeAccess = definitionCodec.encodeRouteAccess(Map.of());
        publish(policyProperties.getEndpointDefinitionDataId(), endpoints);
        publish(policyProperties.getClientAppDefinitionDataId(), clientApps);
        publish(policyProperties.getChannelAccessDefinitionDataId(), channelAccess);
        publish(policyProperties.getChannelCredentialDefinitionDataId(), channelCredentials);
        publish(policyProperties.getRouteAccessDefinitionDataId(), routeAccess);

        IngressPolicyContent content = new IngressPolicyContent(
                endpointCatalog, Map.of(), Map.of(), Map.of(), Map.of());
        IngressPolicySnapshot bootstrap = new IngressPolicySnapshot(
                0, content, hasher.hash(content), Instant.parse("2026-01-01T00:00:00Z"));
        publish(policyProperties.getDataId(), snapshotCodec.encodeSnapshot(bootstrap));
    }

    private EndpointCatalog endpointCatalog() {
        return new EndpointCatalog(List.of(new EndpointDefinition(
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
                    && snapshotCodec.decodeSnapshot(content).snapshotVersion() == expectedVersion;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isRouteDefinitionReadable() {
        try {
            String content = configService.getConfig(
                    policyProperties.getRouteAccessDefinitionDataId(), GROUP, TimeUnit.SECONDS.toMillis(10));
            return StringUtils.hasText(content)
                    && definitionCodec.decodeRouteAccess(content).containsKey(new EndpointId(ENDPOINT_ID));
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
        configService.removeConfig(policyProperties.getClientAppDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getChannelAccessDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getChannelCredentialDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getRouteAccessDefinitionDataId(), GROUP);
        configService.removeConfig(policyProperties.getDataId(), GROUP);
    }

    private static final class ImmediateLockExecutor implements LockExecutor {
        @Override
        public <T> Optional<T> execute(LockContext context, Callable<T> callable) throws Exception {
            return Optional.ofNullable(callable.call());
        }
    }

    private static final class TestPolicyRollbackState implements PolicyRollbackState {
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
