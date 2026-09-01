package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.CredentialForwardingPolicy;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.SubjectRequirement;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicySnapshotHasher;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodPolicySnapshotStore;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class PolicySnapshotPropagationIT {
    private static final String MANUAL_OBSERVATION_SECONDS_PROPERTY =
            "gateway.it.manual-observation-seconds";
    private static final String DATA_ID = "gateway-ingress-policy.yaml";
    private static final String GROUP = "GATEWAY";
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
    private NacosPolicySnapshotStore firstStore;
    private NacosPolicySnapshotStore secondStore;

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
        configService = NacosFactory.createConfigService(
                NACOS.getHost() + ":" + HOST_HTTP_PORT);
        StoredPolicySnapshotCodec codec = codec();
        String initialContent = codec.encodeSnapshot(snapshot(1, true));
        assertTrue(configService.publishConfig(DATA_ID, GROUP, initialContent, ConfigType.YAML.getType()));
        await(() -> isConfigReadable(configService, initialContent));
        firstStore = store(configService, codec, "first.yaml");
        secondStore = store(configService, codec, "second.yaml");
        firstStore.initialize();
        secondStore.initialize();

        assertTrue(firstStore.compareAndSet(firstStore.current().sourceRevision(), snapshot(2, false)));

        await(() -> secondStore.current().snapshot().snapshotVersion() == 2);
        assertEquals(firstStore.current().snapshot().contentHash(), secondStore.current().snapshot().contentHash());

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

    private NacosPolicySnapshotStore store(
            ConfigService configService,
            StoredPolicySnapshotCodec codec,
            String lastKnownGoodFilename
    ) {
        NacosPolicyProperties properties = new NacosPolicyProperties();
        properties.setReadTimeout(Duration.ofSeconds(10));
        properties.setRecoveryInterval(Duration.ofMinutes(1));
        properties.setLastKnownGoodLocation(temporaryDirectory.resolve(lastKnownGoodFilename));
        return new NacosPolicySnapshotStore(
                new DefaultNacosConfigOperations(configService),
                properties,
                codec,
                new FileLastKnownGoodPolicySnapshotStore(properties.getLastKnownGoodLocation()));
    }

    private static StoredPolicySnapshotCodec codec() {
        PolicySnapshotHasher hasher = new CanonicalPolicySnapshotHasher(
                new CanonicalPolicyContentMapper(), new ObjectMapper());
        return new StoredPolicySnapshotCodec(
                new StoredPolicySnapshotMapper(), hasher, new ObjectMapper().findAndRegisterModules());
    }

    private static IngressPolicySnapshot snapshot(long version, boolean enabled) {
        PolicySnapshotHasher hasher = new CanonicalPolicySnapshotHasher(
                new CanonicalPolicyContentMapper(), new ObjectMapper());
        EndpointId endpointId = new EndpointId("USER_AUTH.LOGIN");
        EndpointCatalog catalog = new EndpointCatalog(List.of(new EndpointDefinition(
                endpointId,
                "user-auth",
                List.of(new EndpointMatcher("POST", "/api/user-auth/login")),
                "user-auth")));
        IngressRouteAccessPolicy route = new IngressRouteAccessPolicy(
                endpointId,
                ChannelCredentialRequirement.IGNORE,
                ChannelRequirement.FORBIDDEN,
                SubjectRequirement.IGNORE,
                Set.of(),
                Set.of(),
                CredentialForwardingPolicy.stripAll(),
                enabled);
        IngressPolicyContent content = new IngressPolicyContent(
                catalog, Map.of(), Map.of(), Map.of(), Map.of(endpointId, route));
        return new IngressPolicySnapshot(
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
