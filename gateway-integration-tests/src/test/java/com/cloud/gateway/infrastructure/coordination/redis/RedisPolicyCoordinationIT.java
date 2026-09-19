package com.cloud.gateway.infrastructure.coordination.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.framework.core.naming.DefaultResourceNameResolver;
import com.cloud.framework.core.naming.Namespaced;
import com.cloud.framework.core.naming.NamespacedResourceNameResolver;
import com.cloud.framework.core.naming.NamespaceResolver;
import com.cloud.framework.lock.DefaultLockExecutor;
import com.cloud.framework.lock.LockContext;
import com.cloud.framework.lock.LockExecutor;
import com.cloud.framework.lock.LockProvider;
import com.cloud.framework.starter.lock.redis.springintegration.SpringIntegrationLockProvider;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackLock;
import com.cloud.gateway.infrastructure.coordination.redis.config.RedisPolicyCoordinationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisPolicyCoordinationIT {
    private static final String MANUAL_OBSERVATION_SECONDS_PROPERTY =
            "gateway.it.manual-observation-seconds";
    private static final String NAMESPACE = "gateway-it";
    private static final String LEASE_NAMESPACE = NAMESPACE + "-lease";
    private static final String OTHER_NAMESPACE = NAMESPACE + "-other";
    private static final String PUBLISH_LOCK_KEY = NAMESPACE + ":publish:ingress-access";
    private static final String ROLLBACK_STATE_KEY = NAMESPACE + ":access:rollback-state";
    private static final Duration LEASE_TIME = Duration.ofSeconds(30);
    private static final Duration SHORT_LEASE_TIME = Duration.ofSeconds(1);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.4.4"))
            .withExposedPorts(6379);

    @TempDir Path temporaryDirectory;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisLockRegistry lockRegistry;
    private AccessPublishLock publishLock;
    private com.cloud.gateway.application.configuration.publication.AccessRollbackLock rollbackLock;
    private RedisAccessRollbackState rollbackState;
    private final List<RedisLockRegistry> createdLockRegistries = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        lockRegistry = lockRegistry(NAMESPACE, LEASE_TIME);
        LockExecutor lockExecutor = new DefaultLockExecutor(lockProvider(lockRegistry));
        publishLock = new AccessPublishLock(lockExecutor);
        rollbackLock = new com.cloud.gateway.application.configuration.publication.AccessRollbackLock(lockExecutor);
        rollbackState = rollbackState(NAMESPACE);
    }

    @AfterEach
    void tearDown() {
        for (RedisLockRegistry registry : createdLockRegistries) {
            registry.destroy();
        }
        createdLockRegistries.clear();
        Set<String> remainingKeys = redisTemplate.keys("gateway-it*");
        if (remainingKeys != null && !remainingKeys.isEmpty()) {
            redisTemplate.delete(remainingKeys);
        }
        connectionFactory.destroy();
    }

    @Test
    void shouldSerializeConcurrentPublishersThroughRealRedisLock() throws Exception {
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicReference<String> winnerResult = new AtomicReference<>();
        AtomicBoolean loserExecuted = new AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            executor.submit(() -> {
                winnerResult.set(publishLock.execute(() -> {
                    lockAcquired.countDown();
                    releaseWinner.await(30, TimeUnit.SECONDS);
                    return "published";
                }));
                return null;
            });
            assertTrue(lockAcquired.await(10, TimeUnit.SECONDS), "Winner did not acquire the publish lock");
            await(() -> isKeyPresent(PUBLISH_LOCK_KEY));

            ApplicationException loserException = assertThrows(
                    ApplicationException.class,
                    () -> publishLock.execute(() -> {
                        loserExecuted.set(true);
                        return "loser";
                    })
            );
            assertEquals(
                    ApplicationError.APP_POLICY_PUBLISH_LOCK_UNAVAILABLE.getErrorCode(),
                    loserException.getErrorCode());
            assertFalse(loserExecuted.get(), "Loser business action must not run while lock is held");
        }
        finally {
            releaseWinner.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals("published", winnerResult.get(), "Exactly one publish business result takes effect");
        await(() -> !isKeyPresent(PUBLISH_LOCK_KEY));
        assertEquals("republished", publishLock.execute(() -> "republished"));

        manualObservationPoint();
    }

    @Test
    void shouldReleasePublishLockWhenPublishActionFails() {
        assertThrows(IllegalStateException.class, () -> publishLock.execute(() -> {
            throw new IllegalStateException("publish failed");
        }));

        await(() -> !isKeyPresent(PUBLISH_LOCK_KEY));
        assertEquals("recovered", publishLock.execute(() -> "recovered"));
    }

    @Test
    void shouldExpireAbandonedPublishLockAfterLease() {
        RedisLockRegistry holderRegistry = lockRegistry(LEASE_NAMESPACE, SHORT_LEASE_TIME);
        LockProvider holderProvider = lockProvider(holderRegistry);
        Lock abandonedLock = holderProvider.obtain("publish", "ingress-access");
        abandonedLock.lock();
        await(() -> isKeyPresent(LEASE_NAMESPACE + ":publish:ingress-access"));

        LockExecutor contenderExecutor = new DefaultLockExecutor(lockProvider(lockRegistry(LEASE_NAMESPACE, LEASE_TIME)));
        await(() -> {
            try {
                return contenderExecutor.execute(
                        new LockContext("publish", "ingress-access"),
                        () -> Boolean.TRUE
                ).orElse(false);
            } catch (Exception ignored) {
                return false;
            }
        });
    }

    @Test
    void shouldPersistRollbackStateAcrossAdapterInstancesWithoutTtl() {
        RedisAccessRollbackState secondInstance = rollbackState(NAMESPACE);

        assertFalse(rollbackState.isActive());
        rollbackState.activate();

        assertTrue(rollbackState.isActive());
        assertTrue(secondInstance.isActive());
        assertEquals(Long.valueOf(-1), redisTemplate.getExpire(ROLLBACK_STATE_KEY));

        rollbackState.activate();
        assertTrue(secondInstance.isActive(), "Activation is idempotent for existing state");

        secondInstance.clear();
        assertFalse(rollbackState.isActive());
        assertFalse(secondInstance.isActive());

        rollbackState.clear();
        assertFalse(rollbackState.isActive());
    }

    @Test
    void shouldIsolateRollbackStateByNamespace() {
        RedisAccessRollbackState otherNamespaceState = rollbackState(OTHER_NAMESPACE);

        rollbackState.activate();

        assertTrue(rollbackState.isActive());
        assertFalse(otherNamespaceState.isActive());
        assertTrue(isKeyPresent(ROLLBACK_STATE_KEY));
        assertFalse(isKeyPresent(OTHER_NAMESPACE + ":access:rollback-state"));
    }

    private RedisLockRegistry lockRegistry(String registryKey, Duration leaseTime) {
        RedisLockRegistry registry = new RedisLockRegistry(connectionFactory, registryKey, leaseTime.toMillis());
        createdLockRegistries.add(registry);
        return registry;
    }

    private SpringIntegrationLockProvider lockProvider(RedisLockRegistry registry) {
        return new SpringIntegrationLockProvider(registry, new DefaultResourceNameResolver());
    }

    private RedisAccessRollbackState rollbackState(String namespace) {
        RedisPolicyCoordinationProperties properties = new RedisPolicyCoordinationProperties();
        properties.setNamespace(namespace);
        NamespaceResolver namespaceResolver = Namespaced::getNamespace;
        AccessRollbackStateKeyResolver keyResolver = new AccessRollbackStateKeyResolver(
                new NamespacedResourceNameResolver(namespaceResolver, properties));
        return new RedisAccessRollbackState(redisTemplate, keyResolver);
    }

    private boolean isKeyPresent(String key) {
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
        Path marker = temporaryDirectory.resolve("redis-access-coordination-observation.active");
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
                throw new AssertionError("Timed out waiting for Redis access coordination state");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
        }
    }
}
