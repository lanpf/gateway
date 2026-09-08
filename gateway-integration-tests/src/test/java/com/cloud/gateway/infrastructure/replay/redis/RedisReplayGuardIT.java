package com.cloud.gateway.infrastructure.replay.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.framework.core.naming.Namespaced;
import com.cloud.framework.core.naming.NamespacedResourceNameResolver;
import com.cloud.framework.core.naming.NamespaceResolver;
import com.cloud.gateway.infrastructure.replay.redis.config.RedisReplayGuardProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisReplayGuardIT {
    private static final String MANUAL_OBSERVATION_SECONDS_PROPERTY =
            "gateway.it.manual-observation-seconds";
    private static final String NAMESPACE = "gateway-it";
    private static final String OTHER_NAMESPACE = NAMESPACE + "-other";
    private static final String EVIDENCE_KEY = "USER_AUTH.LOGIN:1717430400000:nonce-1001";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.4.4"))
            .withExposedPorts(6379);

    @TempDir Path temporaryDirectory;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private final List<LettuceConnectionFactory> createdConnectionFactories = new ArrayList<>();
    private final List<ReplayGuardInstance> createdInstances = new ArrayList<>();

    @BeforeEach
    void setUp() {
        connectionFactory = connectionFactory();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        Set<String> remainingKeys = redisTemplate.keys("gateway-it*");
        if (remainingKeys != null && !remainingKeys.isEmpty()) {
            redisTemplate.delete(remainingKeys);
        }
        for (ReplayGuardInstance instance : createdInstances) {
            instance.destroy();
        }
        createdInstances.clear();
        for (LettuceConnectionFactory factory : createdConnectionFactories) {
            factory.destroy();
        }
        createdConnectionFactories.clear();
        connectionFactory.destroy();
    }

    @Test
    void shouldRegisterEvidenceAtomicallyAcrossGatewayInstances() throws Exception {
        ReplayGuardInstance firstInstance = instance(NAMESPACE);
        ReplayGuardInstance secondInstance = instance(NAMESPACE);

        int contenders = 10;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CyclicBarrier startBarrier = new CyclicBarrier(contenders);
        List<Future<Boolean>> registrations = new ArrayList<>();
        Instant expiresAt = Instant.now().plusSeconds(30);

        try {
            for (int i = 0; i < contenders; i++) {
                RedisReplayGuard guard = i % 2 == 0 ? firstInstance.guard() : secondInstance.guard();
                registrations.add(executor.submit(() -> {
                    startBarrier.await(10, TimeUnit.SECONDS);
                    return register(guard, expiresAt);
                }));
            }

            long successes = 0;
            for (Future<Boolean> registration : registrations) {
                if (registration.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes, "Exactly one gateway instance may register the same replay evidence");
        }
        finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertTrue(hasKey("gateway-it:replay:" + EVIDENCE_KEY));

        manualObservationPoint();
    }

    @Test
    void shouldAllowReRegistrationAfterEvidenceTtlExpires() throws Exception {
        RedisReplayGuard guard = instance(NAMESPACE).guard();

        assertTrue(register(guard, Instant.now().plusMillis(800)));
        await(() -> !hasKey("gateway-it:replay:" + EVIDENCE_KEY));

        assertTrue(register(guard, Instant.now().plusSeconds(30)));
    }

    @Test
    void shouldRejectExpiredEvidenceWithoutTouchingRedis() throws Exception {
        RedisReplayGuard guard = instance(NAMESPACE).guard();

        assertFalse(register(guard, Instant.now().minusSeconds(1)));
        assertFalse(register(guard, Instant.now()));

        assertFalse(hasKey("gateway-it:replay:" + EVIDENCE_KEY));
    }

    @Test
    void shouldIsolateEvidenceByNamespace() throws Exception {
        RedisReplayGuard firstGuard = instance(NAMESPACE).guard();
        RedisReplayGuard secondGuard = instance(OTHER_NAMESPACE).guard();

        assertTrue(register(firstGuard, Instant.now().plusSeconds(30)));
        assertTrue(register(secondGuard, Instant.now().plusSeconds(30)));

        assertFalse(register(firstGuard, Instant.now().plusSeconds(30)));
        assertFalse(register(secondGuard, Instant.now().plusSeconds(30)));

        assertTrue(hasKey("gateway-it:replay:" + EVIDENCE_KEY));
        assertTrue(hasKey("gateway-it-other:replay:" + EVIDENCE_KEY));
    }

    private boolean register(RedisReplayGuard guard, Instant expiresAt) throws Exception {
        return guard.register(EVIDENCE_KEY, expiresAt).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private ReplayGuardInstance instance(String namespace) {
        LettuceConnectionFactory instanceFactory = connectionFactory();
        RedisReplayGuardProperties properties = new RedisReplayGuardProperties();
        properties.setNamespace(namespace);
        NamespaceResolver namespaceResolver = Namespaced::getNamespace;
        ReplayGuardKeyResolver keyResolver = new ReplayGuardKeyResolver(
                new NamespacedResourceNameResolver(namespaceResolver, properties));
        RedisReplayGuard guard = new RedisReplayGuard(
                new ReactiveStringRedisTemplate(instanceFactory), keyResolver, Clock.systemUTC());
        ReplayGuardInstance created = new ReplayGuardInstance(instanceFactory, guard);
        createdInstances.add(created);
        return created;
    }

    private LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        createdConnectionFactories.add(factory);
        return factory;
    }

    private boolean hasKey(String key) {
        Boolean present = redisTemplate.hasKey(key);
        return present != null && present;
    }

    private void manualObservationPoint() throws Exception {
        System.out.println("Redis endpoint: redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        System.out.println("Inspect with: redis-cli -h " + REDIS.getHost() + " -p " + REDIS.getMappedPort(6379));
        long observationSeconds = Long.getLong(MANUAL_OBSERVATION_SECONDS_PROPERTY, 0L);
        if (observationSeconds <= 0) {
            return;
        }
        Path marker = temporaryDirectory.resolve("redis-replay-guard-observation.active");
        Files.writeString(marker, "Delete this file to finish manual observation early.\n");
        System.out.println("Redis manual observation is active for at most " + observationSeconds + " seconds.");
        System.out.println("Delete this marker to finish early: " + marker);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(observationSeconds);
        while (Files.exists(marker) && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(250));
        }
        Files.deleteIfExists(marker);
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for Redis replay evidence state");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
        }
    }

    private record ReplayGuardInstance(LettuceConnectionFactory connectionFactory, RedisReplayGuard guard) {

        private void destroy() {
            connectionFactory.destroy();
        }
    }
}
