package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.cloud.gateway.application.access.publication.AccessSnapshotHasher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodAccessSnapshotStore;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.DefaultNacosConfigOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class IngressAccessSnapshotPropagationIT {
    private static final String MANUAL_OBSERVATION_SECONDS_PROPERTY =
            "gateway.it.manual-observation-seconds";
    private static final String DATA_ID = "gateway-policy.yaml";
    private static final String GROUP = "GATEWAY";
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
    private StoredAccessSnapshotCodec codec;
    private NacosAccessSnapshotStore firstStore;
    private NacosAccessSnapshotStore secondStore;

    @BeforeEach
    void setUp() throws Exception {
        configService = NacosFactory.createConfigService(
                NACOS.getHost() + ":" + hostHttpPort);
        codec = codec();
        String initialContent = codec.encodeSnapshot(snapshot(1, true));
        assertTrue(configService.publishConfig(DATA_ID, GROUP, initialContent, ConfigType.YAML.getType()));
        await(() -> isConfigReadable(configService, initialContent));
        firstStore = store(configService, codec, "first.yaml");
        secondStore = store(configService, codec, "second.yaml");
        firstStore.initialize();
        secondStore.initialize();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (firstStore != null) {
            firstStore.close();
        }
        if (secondStore != null) {
            secondStore.close();
        }
        if (configService != null) {
            configService.removeConfig(DATA_ID, GROUP);
            configService.shutDown();
        }
    }

    @Test
    void shouldPublishWithCasAndPropagateToAnotherGatewayInstance() throws Exception {
        assertTrue(firstStore.compareAndSet(firstStore.current().sourceRevision(), snapshot(2, false)));

        await(() -> secondStore.current().snapshot().version() == 2);
        assertEquals(firstStore.current().snapshot().contentHash(), secondStore.current().snapshot().contentHash());

        manualObservationPoint();
    }

    @Test
    void shouldKeepInFlightRequestOnSingleSnapshotVersionDuringPropagation() {
        IngressAccessSnapshot inFlightSnapshot = firstStore.current().snapshot();
        assertEquals(1, inFlightSnapshot.version());
        assertTrue(inFlightSnapshot.content().endpointPolicies().get(new EndpointId("USER_AUTH.LOGIN")).enabled());

        assertTrue(firstStore.compareAndSet(firstStore.current().sourceRevision(), snapshot(2, false)));

        await(() -> secondStore.current().snapshot().version() == 2);
        assertEquals(2, firstStore.current().snapshot().version());

        assertEquals(1, inFlightSnapshot.version(),
                "In-flight request must keep using the single snapshot version it originally read");
        assertTrue(inFlightSnapshot.content().endpointPolicies().get(new EndpointId("USER_AUTH.LOGIN")).enabled());
        assertFalse(firstStore.current().snapshot().content().endpointPolicies().get(new EndpointId("USER_AUTH.LOGIN")).enabled(),
                "Fresh reads after propagation must observe the new version atomically, never a mixed state");
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
        Path marker = temporaryDirectory.resolve("policy-snapshot-propagation-observation.active");
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

    private NacosAccessSnapshotStore store(
            ConfigService configService,
            StoredAccessSnapshotCodec codec,
            String lastKnownGoodFilename
    ) {
        NacosPolicyProperties properties = new NacosPolicyProperties();
        properties.setReadTimeout(Duration.ofSeconds(10));
        properties.setRecoveryInterval(Duration.ofMinutes(1));
        properties.setLastKnownGoodLocation(temporaryDirectory.resolve(lastKnownGoodFilename));
        return new NacosAccessSnapshotStore(
                new DefaultNacosConfigOperations(configService),
                properties,
                codec,
                new FileLastKnownGoodAccessSnapshotStore(properties.getLastKnownGoodLocation()));
    }

    private static StoredAccessSnapshotCodec codec() {
        com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher = new CanonicalAccessSnapshotHasher(
                new CanonicalPolicyContentMapper(), new ObjectMapper());
        return new StoredAccessSnapshotCodec(
                new StoredAccessSnapshotMapper(), hasher, new ObjectMapper().findAndRegisterModules(), com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly());
    }

    private static IngressAccessSnapshot snapshot(long version, boolean enabled) {
        com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher = new CanonicalAccessSnapshotHasher(
                new CanonicalPolicyContentMapper(), new ObjectMapper());
        EndpointId endpointId = new EndpointId("USER_AUTH.LOGIN");
        EndpointRegistry catalog = new EndpointRegistry(List.of(new Endpoint(
                endpointId,
                "user-auth",
                List.of(new EndpointMatcher("POST", "/api/user-auth/login")),
                "user-auth")));
        EndpointAccess route = new EndpointAccess(
                endpointId,
                ClientVerificationRequirement.FORBIDDEN,
                ClientVerificationMode.IGNORE,
                SubjectAuthenticationRequirement.IGNORE,
                Set.of(),
                Set.of(),
                CredentialForwarding.stripAll(),
                enabled ? EndpointStatus.ENABLED
                        : EndpointStatus.DISABLED);
        IngressAccessContent content = new IngressAccessContent(
                catalog, Map.of(), Map.of(), Map.of(endpointId, route));
        return new IngressAccessSnapshot(
                version,
                content,
                hasher.hash(content),
                Instant.parse("2026-08-26T00:00:00Z").plusSeconds(version));
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for Nacos policy propagation");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
        }
    }

    private static boolean isConfigReadable(ConfigService configService, String expectedContent) {
        try {
            return expectedContent.equals(configService.getConfig(DATA_ID, GROUP, TimeUnit.SECONDS.toMillis(10)));
        } catch (Exception ignored) {
            return false;
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
