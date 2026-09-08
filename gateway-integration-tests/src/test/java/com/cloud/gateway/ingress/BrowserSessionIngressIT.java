package com.cloud.gateway.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.Result;
import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.policy.AuthorizationHeaderPolicy;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.ClientAppStatus;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.CredentialForwardingPolicy;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.SubjectRequirement;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicySnapshotHasher;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.userauth.api.authentication.CreateSessionHandoffApiCommandOutput;
import com.cloud.userauth.api.authentication.IssueAuthChallengeApiCommandOutput;
import com.cloud.userauth.api.authentication.MobileOtpLoginApiCommand;
import com.cloud.userauth.api.authentication.MobileOtpLoginApiCommandOutput;
import com.cloud.userauth.api.enums.AuthChallengeSceneApiEnum;
import com.cloud.userauth.api.enums.AuthChallengeTypeApiEnum;
import com.alibaba.nacos.api.config.ConfigType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 跨服务 Browser Session 入口场景：真实 user-auth 应用、真实 gateway 应用与真实
 * Nacos/MySQL/Redis 基础设施共同验证职责切分后的协作契约。
 */
@Testcontainers(disabledWithoutDocker = true)
class BrowserSessionIngressIT {
    private static final Logger log = LoggerFactory.getLogger(BrowserSessionIngressIT.class);
    private static final String GROUP = "GATEWAY";
    private static final String ENDPOINT_ID = "USER_AUTH.BROWSER_SESSION_API";
    private static final String CLIENT_APP_ID = "mini-program";
    private static final String CHANNEL_CODE = "DIRECT";
    private static final String ALLOWED_ORIGIN = "https://h5.example.com";
    private static final String MOBILE = "13800001234";
    private static final String FIXED_CODE = "123456";
    private static final int USER_AUTH_PORT = availablePort();
    private static final int GATEWAY_PORT = availablePort();

    @Container
    private static final FixedPortNacosContainer NACOS =
            new FixedPortNacosContainer(availablePort());

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.10"));

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.4.4")).withExposedPorts(6379);

    private final Path temporaryDirectory = createTempDirectory();
    private ConfigService configService;
    private ConfigurableApplicationContext userAuthContext;
    private Process gatewayProcess;
    private Process userAuthProcess;
    private ConfigurableApplicationContext gatewayContext;

    @BeforeEach
    void setUp() throws Exception {
        configService = NacosFactory.createConfigService(
                NACOS.getHost() + ":" + NACOS.getMappedPort(8848));
        seedSnapshot();
        startUserAuth();
        awaitHttp("user-auth", USER_AUTH_PORT);
        startGateway();
        awaitHttp("gateway", GATEWAY_PORT);
    }

    @AfterEach
    void tearDown() {
        for (Process process : new Process[]{gatewayProcess, userAuthProcess}) {
            if (process != null) {
                process.destroy();
                process.destroyForcibly();
            }
        }
        if (configService != null) {
            try {
                configService.shutDown();
            } catch (Exception ignored) {
                // Container teardown follows.
            }
        }
    }

    @Test
    void shouldEndBrowserSessionThroughGatewayIngressChain() throws Exception {
        RestClient userAuth = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + USER_AUTH_PORT)
                .build();
        String browserSession = exchangeBrowserSession(userAuth);

        manualObservationBreakpoint();

        ResponseEntity<Void> ended = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + GATEWAY_PORT)
                .build()
                .post()
                .uri("/api/user-auth/browser-sessions/end")
                .header(RequestHeader.CLIENT_APP_ID, CLIENT_APP_ID)
                .header(RequestHeader.CHANNEL_CODE, CHANNEL_CODE)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.COOKIE, "BROWSER_SESSION=" + browserSession)
                .retrieve()
                .toBodilessEntity();

        assertEquals(200, ended.getStatusCode().value());
        String clearCookie = ended.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(clearCookie, "Ending a browser session must clear the cookie");
        assertTrue(clearCookie.contains("BROWSER_SESSION=") && clearCookie.contains("Max-Age=0"),
                () -> "Unexpected clear cookie: " + clearCookie);

        Result<Map<String, Object>> afterEnd = verifyBrowserSession(userAuth, browserSession);
        assertEquals(false, afterEnd.getData().get("verified"));
    }

    @Test
    void shouldRejectUnknownBrowserSessionCookieWithoutForwarding() {
        ResponseEntity<Void> response = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + GATEWAY_PORT)
                .build()
                .post()
                .uri("/api/user-auth/browser-sessions/end")
                .header(RequestHeader.CLIENT_APP_ID, CLIENT_APP_ID)
                .header(RequestHeader.CHANNEL_CODE, CHANNEL_CODE)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.COOKIE, "BROWSER_SESSION=bogus-credential")
                .retrieve()
                .onStatus(status -> true, (ignoredRequest, ignoredResponse) -> { })
                .toBodilessEntity();

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void shouldRejectUnsafeCookieRequestOutsideAllowedBrowserOrigin() {
        ResponseEntity<Void> response = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + GATEWAY_PORT)
                .build()
                .post()
                .uri("/api/user-auth/browser-sessions/end")
                .header(RequestHeader.CLIENT_APP_ID, CLIENT_APP_ID)
                .header(RequestHeader.CHANNEL_CODE, CHANNEL_CODE)
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.COOKIE, "BROWSER_SESSION=bogus-credential")
                .retrieve()
                .onStatus(status -> true, (ignoredRequest, ignoredResponse) -> { })
                .toBodilessEntity();

        assertEquals(403, response.getStatusCode().value());
    }

    private String exchangeBrowserSession(RestClient userAuth) {
        Result<IssueAuthChallengeApiCommandOutput> challenge = post(userAuth, "/api/challenges",
                Map.of(
                        "challengeType", AuthChallengeTypeApiEnum.SMS_OTP,
                        "target", MOBILE,
                        "scene", AuthChallengeSceneApiEnum.LOGIN),
                new ParameterizedTypeReference<>() {
                });
        Result<MobileOtpLoginApiCommandOutput> login = post(userAuth, "/api/login/mobile-otp",
                new MobileOtpLoginApiCommand(
                        challenge.getData().challengeId(),
                        FIXED_CODE,
                        CLIENT_APP_ID,
                        "IOS",
                        "1.0.0",
                        CHANNEL_CODE,
                        "device-1",
                        "PHONE",
                        "Browser Session ingress IT"),
                new ParameterizedTypeReference<>() {
                });
        Result<CreateSessionHandoffApiCommandOutput> handoff = post(userAuth, "/api/handoffs",
                Map.of("target", "BROWSER_SESSION"),
                authenticatedHeaders(login.getData()),
                new ParameterizedTypeReference<>() {
                });
        ResponseEntity<Result<Map<String, Object>>> exchange = userAuth.post()
                .uri("/api/handoffs/exchange")
                .header(RequestHeader.CLIENT_APP_ID, CLIENT_APP_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ticket", handoff.getData().ticket(), "target", "BROWSER_SESSION"))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        String setCookie = exchange.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie, "Exchange must deliver the browser session cookie");
        return HttpCookie.parse(setCookie).get(0).getValue();
    }

    private Map<String, Object> authenticatedHeaders(MobileOtpLoginApiCommandOutput login) {
        return Map.of(
                RequestHeader.CLIENT_APP_ID, CLIENT_APP_ID,
                RequestHeader.SUBJECT_TYPE, "HOST_SESSION",
                RequestHeader.USER_ID, login.userId().toString(),
                RequestHeader.SESSION_ID, login.sessionId());
    }

    private Result<Map<String, Object>> verifyBrowserSession(
            RestClient userAuth, String browserSession) {
        return post(userAuth, "/internal/browser-sessions/verify",
                Map.of("credential", browserSession),
                new ParameterizedTypeReference<>() {
                });
    }

    private <T> Result<T> post(
            RestClient restClient,
            String uri,
            Object body,
            ParameterizedTypeReference<Result<T>> type
    ) {
        ResponseEntity<Result<T>> response = restClient.post()
                .uri(uri)
                .header(RequestHeader.CLIENT_APP_ID, CLIENT_APP_ID)
                .header(RequestHeader.CHANNEL_CODE, CHANNEL_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(type);
        Result<T> result = response.getBody();
        assertNotNull(result, () -> "Missing response body for " + uri);
        assertTrue(result.isSuccess(), () -> "Unexpected failure for " + uri + ": " + result);
        return result;
    }

    private <T> Result<T> post(
            RestClient restClient,
            String uri,
            Object body,
            Map<String, Object> headers,
            ParameterizedTypeReference<Result<T>> type
    ) {
        ResponseEntity<Result<T>> response = restClient.post()
                .uri(uri)
                .headers(header -> headers.forEach(
                        (name, value) -> header.set(name, String.valueOf(value))))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(type);
        Result<T> result = response.getBody();
        assertNotNull(result, () -> "Missing response body for " + uri);
        assertTrue(result.isSuccess(), () -> "Unexpected failure for " + uri + ": " + result);
        return result;
    }

    private void seedSnapshot() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        PolicySnapshotHasher hasher = new CanonicalPolicySnapshotHasher(
                new CanonicalPolicyContentMapper(), objectMapper);
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                new StoredPolicySnapshotMapper(), objectMapper);
        NacosPolicyProperties properties = nacosPolicyProperties();

        EndpointCatalog endpointCatalog = new EndpointCatalog(List.of(new EndpointDefinition(
                new EndpointId(ENDPOINT_ID),
                "user-auth",
                List.of(new EndpointMatcher("POST", "/api/user-auth/browser-sessions/**")),
                "user-auth")));
        Map<ClientAppId, IngressClientAppPolicy> clientApps = Map.of(
                new ClientAppId(CLIENT_APP_ID),
                new IngressClientAppPolicy(
                        new ClientAppId(CLIENT_APP_ID),
                        ClientAppStatus.ENABLED,
                        "WECHAT_MINI_PROGRAM",
                        Set.of(ALLOWED_ORIGIN)));
        Map<ChannelCode, IngressChannelAccessPolicy> channelAccess = Map.of(
                new ChannelCode(CHANNEL_CODE),
                new IngressChannelAccessPolicy(
                        new ChannelCode(CHANNEL_CODE),
                        Set.of(new ClientAppId(CLIENT_APP_ID))));
        Map<EndpointId, IngressRouteAccessPolicy> routeAccess = Map.of(
                new EndpointId(ENDPOINT_ID),
                new IngressRouteAccessPolicy(
                        new EndpointId(ENDPOINT_ID),
                        ChannelCredentialRequirement.IGNORE,
                        ChannelRequirement.REQUIRED,
                        SubjectRequirement.BROWSER_SESSION_REQUIRED,
                        Set.of(),
                        Set.of(),
                        new CredentialForwardingPolicy(
                                AuthorizationHeaderPolicy.STRIP,
                                CookieForwardingMode.ALLOW_LIST,
                                Set.of("BROWSER_SESSION")),
                        true));

        publish(properties.getEndpointDefinitionDataId(),
                codec.encodeEndpointCatalog(endpointCatalog));
        publish(properties.getClientAppDefinitionDataId(), codec.encodeClientApps(clientApps));
        publish(properties.getChannelAccessDefinitionDataId(),
                codec.encodeChannelAccess(channelAccess));
        publish(properties.getChannelCredentialDefinitionDataId(),
                codec.encodeChannelCredentials(Map.of()));
        publish(properties.getRouteAccessDefinitionDataId(), codec.encodeRouteAccess(routeAccess));

        IngressPolicyContent content = new IngressPolicyContent(
                endpointCatalog, clientApps, channelAccess, Map.of(), routeAccess);
        IngressPolicySnapshot snapshot = new IngressPolicySnapshot(
                0, content, hasher.hash(content), Instant.parse("2026-01-01T00:00:00Z"));
        publish(properties.getDataId(), snapshotCodec(objectMapper, hasher).encodeSnapshot(snapshot));

        await(() -> readableSnapshot(properties));
    }

    private StoredPolicySnapshotCodec snapshotCodec(
            ObjectMapper objectMapper, PolicySnapshotHasher hasher) {
        return new StoredPolicySnapshotCodec(
                new StoredPolicySnapshotMapper(), hasher, objectMapper);
    }

    private NacosPolicyProperties nacosPolicyProperties() {
        NacosPolicyProperties properties = new NacosPolicyProperties();
        properties.setGroup(GROUP);
        return properties;
    }

    private void publish(String dataId, String content) throws Exception {
        assertTrue(configService.publishConfig(dataId, GROUP, content, ConfigType.YAML.getType()));
    }

    private boolean readableSnapshot(NacosPolicyProperties properties) {
        try {
            String content = configService.getConfig(
                    properties.getDataId(), GROUP, TimeUnit.SECONDS.toMillis(5));
            return content != null && !content.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void startUserAuth() throws Exception {
        Path jar = workspaceRoot().resolve(
                "user-auth/user-auth-boot/target/user-auth-boot-0.4.3-SNAPSHOT.jar");
        assertTrue(Files.exists(jar),
                () -> "user-auth-boot fat jar missing; run `mvn -pl user-auth-boot -am package -DskipTests` "
                        + "in the user-auth workspace first: " + jar.toAbsolutePath());
        ProcessBuilder builder = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar",
                jar.toString(),
                "--server.port=" + USER_AUTH_PORT,
                "--spring.config.additional-location=optional:file:"
                        + workspaceRoot().resolve("user-auth/config") + "/",
                "--spring.sql.init.schema-locations=file:"
                        + workspaceRoot().resolve("user-auth/config/db/schema.sql"),
                "--spring.datasource.url=" + MYSQL.getJdbcUrl()
                        + "?preserveInstants=true&connectionTimeZone=UTC"
                        + "&forceConnectionTimeZoneToSession=true",
                "--spring.datasource.username=" + MYSQL.getUsername(),
                "--spring.datasource.password=" + MYSQL.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "--spring.cloud.nacos.config.enabled=false",
                "--spring.cloud.nacos.discovery.enabled=false",
                "--spring.profiles.active=it",
                "--user-auth.authentication.oauth2.authorization-server.sas.issuer="
                        + "http://127.0.0.1:" + USER_AUTH_PORT,
                "--user-auth.authentication.oauth2.authorization-server.sas.internal-token-client.token-endpoint="
                        + "http://127.0.0.1:" + USER_AUTH_PORT + "/oauth2/token");
        builder.redirectErrorStream(true);
        builder.redirectOutput(temporaryDirectory.resolve("user-auth-ingress-it.log").toFile());
        builder.directory(workspaceRoot().toFile());
        userAuthProcess = builder.start();
    }

    /** gateway 以独立进程的 fat jar 运行；双应用分别 fork，避免 Web 技术栈类路径互相污染。 */
    private void startGateway() throws Exception {
        Path jar = workspaceRoot().resolve("gateway/gateway-boot/target/gateway-boot-0.3.0-SNAPSHOT.jar");
        assertTrue(Files.exists(jar),
                () -> "gateway-boot fat jar missing; run `mvn -pl gateway-boot -am package -DskipTests` first: "
                        + jar.toAbsolutePath());
        ProcessBuilder builder = new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar",
                jar.toAbsolutePath().toString(),
                "--server.port=" + GATEWAY_PORT,
                "--spring.config.additional-location=optional:file:"
                        + workspaceRoot().resolve("gateway/config/application.yml"),
                "--spring.cloud.nacos.config.server-addr="
                        + NACOS.getHost() + ":" + NACOS.getMappedPort(8848),
                "--spring.cloud.nacos.config.import-check.enabled=false",
                "--spring.cloud.nacos.discovery.enabled=false",
                "--spring.cloud.discovery.client.simple.instances.user-auth[0].uri="
                        + "http://127.0.0.1:" + USER_AUTH_PORT,
                "--gateway.policy.nacos.group=" + GROUP,
                "--gateway.policy.nacos.last-known-good-location="
                        + temporaryDirectory.resolve("gateway-last-known-good.yaml"),
                "--gateway.security.admin-audience=gateway-admin",
                "--debug",
                "--gateway.security.access-token.format=SELF_CONTAINED",
                "--gateway.security.access-token.issuer=http://127.0.0.1:" + USER_AUTH_PORT,
                "--gateway.security.access-token.jwt.set-uri="
                        + "http://127.0.0.1:" + USER_AUTH_PORT + "/oauth2/jwks",
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379));
        builder.redirectErrorStream(true);
        builder.redirectOutput(temporaryDirectory.resolve("gateway-ingress-it.log").toFile());
        builder.directory(workspaceRoot().toFile());
        gatewayProcess = builder.start();
    }

    private static void awaitHttp(String application, int port) {
        await(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection)
                        new URL("http://127.0.0.1:" + port + "/actuator/health").openConnection();
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(1000);
                return connection.getResponseCode() == 200;
            } catch (IOException ignored) {
                return false;
            }
        });
    }

    private void manualObservationBreakpoint() {
        log.info(
                "Manual inspection ready: set a debugger breakpoint here; "
                        + "mysqlContainerId={}, redisContainerId={}, nacosContainerId={}, "
                        + "userAuthPort={}, gatewayPort={}",
                MYSQL.getContainerId(),
                REDIS.getContainerId(),
                NACOS.getContainerId(),
                USER_AUTH_PORT,
                GATEWAY_PORT);
    }

    private static void await(BooleanSupplier condition) {
        await(condition, () -> "Timed out waiting for condition");
    }

    private static void await(BooleanSupplier condition, Supplier<String> message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(message.get());
            }
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
    }

    private static Path workspaceRoot() {
        return Path.of("..", "..").toAbsolutePath().normalize();
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("browser-session-ingress-it");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int availablePort() {
        for (int attempt = 0; attempt < 100; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(20_000, 50_000);
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(candidate));
                return candidate;
            } catch (IOException ignored) {
                // Try another port.
            }
        }
        throw new IllegalStateException("Unable to reserve a port");
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
            waitingFor(Wait.forLogMessage(".*Nacos started successfully.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));
        }
    }
}
