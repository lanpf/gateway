package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.gateway.application.quota.QuotaCommand;
import com.cloud.gateway.application.quota.QuotaCommandService;

import com.cloud.framework.core.naming.NamespaceResolver;
import com.cloud.framework.core.naming.Namespaced;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitConfig;
import com.cloud.gateway.application.quota.QuotaAcquirer;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.capability.ratelimit.RedisFailureMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

/** Real Lua smoke contract; manual observation instructions belong to the root README. */
@Testcontainers
class RateLimitIT {
    private static final String NAMESPACE = "gateway-ratelimit-it";
    private static final String VALUE = "partner-a";
    private static final String OBSERVATION_SECONDS = "gateway.it.manual-observation-seconds";
    private static final String TOKENS = "tokens";
    // Use the repository's already cached Redis version; no production Redis is contacted.
    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.4.4")).withExposedPorts(6379);
    private static final DefaultRedisScript<Long> SEED = new DefaultRedisScript<>("""
            local t = redis.call('TIME')
            local ts = tonumber(t[1]) + tonumber(t[2]) / 1000000 + tonumber(ARGV[2])
            redis.call('HSET', KEYS[1], 'tokens', ARGV[1], 'ts', tostring(ts),
                       'capacity', ARGV[3], 'refill', ARGV[4])
            redis.call('PEXPIRE', KEYS[1], 180000)
            return 1
            """, Long.class);

    private final List<AnnotationConfigApplicationContext> contexts = new ArrayList<>();
    private Node first;
    private Node second;

    @BeforeEach
    void setUp() {
        first = node(NAMESPACE);
        second = node(NAMESPACE);
    }

    @AfterEach
    void tearDown() {
        try {
            if (first != null) {
                // The container is owned by this scenario; never scan or clean an external Redis.
                Set<String> keys = first.redis().keys(NAMESPACE + "*");
                if (keys != null && !keys.isEmpty()) {
                    first.redis().delete(keys);
                }
            }
        } finally {
            contexts.forEach(AnnotationConfigApplicationContext::close);
            contexts.clear();
        }
    }

    @Test
    void shouldShareExactCapacityAcrossIndependentGatewayInstances() throws Exception {
        RateLimitRule rule = rule("concurrent", 7, 1);
        // Freeze refill using a future server timestamp to isolate atomic consumption from test speed.
        seed(first, rule, 7, 60);
        ExecutorService executor = Executors.newFixedThreadPool(24);
        var barrier = new CyclicBarrier(24);
        var acquisitions = new ArrayList<Future<RateLimitDecision>>();
        try {
            for (int i = 0; i < 24; i++) {
                QuotaAcquirer quota = (i % 2 == 0 ? first : second).quota();
                acquisitions.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return quota.tryAcquire(rule, VALUE);
                }));
            }
            long allowed = 0;
            for (Future<RateLimitDecision> acquisition : acquisitions) {
                RateLimitDecision decision = acquisition.get(10, TimeUnit.SECONDS);
                assertFalse(decision.unavailable());
                if (decision.allowed()) {
                    allowed++;
                }
            }
            assertEquals(7, allowed);
            assertEquals(0d, tokens(first, rule));
            assertTrue(first.redis().getExpire(first.keys().bucket(rule, VALUE), TimeUnit.MILLISECONDS) > 0);
            observe(first.keys().bucket(rule, VALUE));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldPreserveFractionalTokensAndIncludeClockRollbackInRetryAfter() {
        RateLimitRule rule = rule("fractional", 1, 1);
        seed(first, rule, 0.5, 60);
        RateLimitDecision initial = first.quota().tryAcquire(rule, VALUE);
        RateLimitDecision repeated = second.quota().tryAcquire(rule, VALUE);
        assertFalse(initial.allowed());
        assertFalse(repeated.allowed());
        assertFalse(initial.unavailable());
        assertEquals(0.5d, tokens(first, rule));
        assertTrue(initial.retryAfter().compareTo(Duration.ofSeconds(59)) > 0);
        assertTrue(initial.retryAfter().compareTo(Duration.ofMillis(60_500)) <= 0);
        assertTrue(repeated.retryAfter().compareTo(initial.retryAfter()) <= 0);
    }

    @Test
    void shouldRefillFromRedisTimeAndReturnFractionalRetryAfter() {
        RateLimitRule rule = rule("server-time", 100, 1);
        seed(first, rule, 0, -10);
        RateLimitDecision acquired = first.quota().tryAcquire(rule, VALUE);
        assertTrue(acquired.allowed());
        assertTrue(acquired.remaining() >= 9);
        assertTrue(acquired.remaining() < 20);
        // A frozen fractional balance isolates retry math while avoiding client-clock control.
        seed(first, rule, 0.999, 1);
        RateLimitDecision denied = second.quota().tryAcquire(rule, VALUE);
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfter().toMillis() > 0);
        assertTrue(denied.retryAfter().toMillis() <= 1001);
        assertEquals(0.999d, tokens(first, rule));
    }

    @Test
    void shouldNotMintFreshQuotaWhenSnapshotParametersChange() {
        RateLimitRule original = rule("stable-rule", 2, 1);
        seed(first, original, 0.25, 60);
        RateLimitRule increased = rule("stable-rule", 100, 10);
        RateLimitDecision denied = second.quota().tryAcquire(increased, VALUE);
        assertFalse(denied.allowed());
        assertFalse(denied.unavailable());
        assertEquals(0.25d, tokens(first, increased));
        assertEquals("100", first.redis().opsForHash().get(first.keys().bucket(original, VALUE), "capacity"));
        assertEquals("10", first.redis().opsForHash().get(first.keys().bucket(original, VALUE), "refill"));
        assertEquals(1, first.redis().keys(NAMESPACE + "*").size());
    }

    @Test
    void shouldSettleOldRateBeforeApplyingNewRateAndCapacity() {
        RateLimitRule oldRule = rule("old-rate", 1000, 1);
        seed(first, oldRule, 0, -10);
        RateLimitRule updated = rule("old-rate", 1000, 100);
        RateLimitDecision acquired = second.quota().tryAcquire(updated, VALUE);
        assertTrue(acquired.allowed());
        assertTrue(acquired.remaining() >= 9);
        assertTrue(acquired.remaining() < 20, "Elapsed time must not refill at the newly increased rate");
    }

    @Test
    void shouldApplySameDimensionRulesAndSelectLargestRetryAfter() {
        RateLimitRule shortWait = rule("short", 1, 10);
        RateLimitRule longWait = rule("long", 1, 1);
        seed(first, shortWait, 0.5, 60);
        seed(first, longWait, 0.5, 60);
        RateLimitDecision combined = acquire(first, List.of(shortWait, longWait), RedisFailureMode.CLOSED);
        assertFalse(combined.allowed());
        assertTrue(combined.retryAfter().compareTo(Duration.ofSeconds(59)) > 0);
        RateLimitDecision shortDecision = second.quota().tryAcquire(shortWait, VALUE);
        assertTrue(combined.retryAfter().compareTo(shortDecision.retryAfter()) > 0);
        assertEquals(0.5d, tokens(first, shortWait));
        assertEquals(0.5d, tokens(first, longWait));
    }

    @Test
    void shouldIsolateApplicationNamespaces() {
        Node other = node(NAMESPACE + "-other");
        RateLimitRule rule = rule("namespace", 1, 1);
        seed(first, rule, 0, 60);
        assertFalse(second.quota().tryAcquire(rule, VALUE).allowed());
        assertTrue(other.quota().tryAcquire(rule, VALUE).allowed());
        assertNotEquals(first.keys().bucket(rule, VALUE), other.keys().bucket(rule, VALUE));
    }

    @Test
    void shouldFailClosedOrUseBoundedLocalQuotaOnRealRedisError() {
        RateLimitRule rule = rule("wrong-type", 1, 1);
        first.redis().opsForValue().set(first.keys().bucket(rule, VALUE), "invalid-bucket", Duration.ofMinutes(1));
        assertTrue(acquire(first, List.of(rule), RedisFailureMode.CLOSED).unavailable());
        assertTrue(acquire(first, List.of(rule), RedisFailureMode.OPEN_WITH_LOCAL).allowed());
        RateLimitDecision denied = acquire(first, List.of(rule), RedisFailureMode.OPEN_WITH_LOCAL);
        assertFalse(denied.allowed());
        assertFalse(denied.unavailable());
        assertTrue(denied.retryAfter().compareTo(Duration.ZERO) > 0);
    }

    private Node node(String namespace) {
        var context = new AnnotationConfigApplicationContext();
        contexts.add(context);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "ratelimit-it", Map.of("gateway.ratelimit.redis.namespace", namespace)));
        context.registerBean(NamespaceResolver.class, () -> Namespaced::getNamespace);
        context.registerBean(LettuceConnectionFactory.class,
                () -> new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379)));
        context.registerBean(StringRedisTemplate.class,
                () -> new StringRedisTemplate(context.getBean(LettuceConnectionFactory.class)));
        context.register(RateLimitRedisAutoConfiguration.class);
        context.refresh();
        return new Node(context.getBean(QuotaAcquirer.class), context.getBean(StringRedisTemplate.class),
                context.getBean(RateLimitKeyResolver.class), context.getBean(QuotaCommandService.class));
    }

    private void seed(Node node, RateLimitRule rule, double tokens, int serverTimeOffsetSeconds) {
        assertEquals(1L, node.redis().execute(SEED, List.of(node.keys().bucket(rule, VALUE)),
                Double.toString(tokens), Integer.toString(serverTimeOffsetSeconds),
                Integer.toString(rule.capacity()), Integer.toString(rule.refillPerSecond())));
    }

    private double tokens(Node node, RateLimitRule rule) {
        return Double.parseDouble((String) node.redis().opsForHash().get(node.keys().bucket(rule, VALUE), TOKENS));
    }

    private static RateLimitRule rule(String id, int capacity, int refill) {
        return new RateLimitRule(id, RateLimitDimension.CLIENT, capacity, refill, RateLimitMode.EXACT);
    }

    private void observe(String key) throws InterruptedException {
        System.out.printf("RateLimitIT observation: docker exec %s redis-cli HGETALL %s%n", REDIS.getContainerId(), key);
        System.out.printf("Expected tokens=0; inspect expiry: docker exec %s redis-cli PTTL %s%n",
                REDIS.getContainerId(), key);
        long seconds = Math.min(60, Math.max(0, Long.getLong(OBSERVATION_SECONDS, 0L)));
        if (seconds > 0) {
            TimeUnit.SECONDS.sleep(seconds);
        }
    }

    private static RateLimitDecision acquire(Node node, List<RateLimitRule> rules, RedisFailureMode failure) {
        return node.service().acquire(new QuotaCommand(List.of(new RateLimitConfig(rules, failure)),
                Map.of(RateLimitDimension.CLIENT, VALUE), false), new java.util.HashSet<>());
    }

    private record Node(QuotaAcquirer quota, StringRedisTemplate redis, RateLimitKeyResolver keys,
            QuotaCommandService service) { }
}
