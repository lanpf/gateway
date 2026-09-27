package com.cloud.gateway.infrastructure.ratelimit.redis;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.application.quota.QuotaCommand;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitPolicy;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRuleId;
import com.cloud.gateway.domain.capability.ratelimit.RedisFailureMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MicrometerQuotaObserverTest {
    private static final RateLimitRule RULE = new RateLimitRule("stable", RateLimitDimension.CLIENT, 1, 1, RateLimitMode.EXACT);

    @Test
    void shouldPreserveMetricNameAndLowCardinalityOutcomeLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            MicrometerQuotaObserver observer = new MicrometerQuotaObserver(registry);
            observer.record(RULE, RateLimitDecision.allowed(RULE.dimension(), "private-identity", 0));
            observer.record(RULE, RateLimitDecision.rejected(RULE.dimension(), "private-identity", Duration.ofSeconds(1)));
            observer.record(RULE, RateLimitDecision.unavailable(RULE.dimension(), "private-identity"));
            for (String outcome : List.of("allowed", "denied", "unavailable")) {
                assertEquals(1, registry.get("gateway.quota.decisions")
                        .tags("dimension", "CLIENT", "decision", outcome).counter().count());
            }
            registry.getMeters().forEach(meter -> assertEquals(2, meter.getId().getTags().size()));
        } finally {
            registry.close();
        }
    }

    @Test
    void shouldObserveFinalFallbackDecisionExactlyOnceAcrossRepeatedStages() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            QuotaCommandService service = new QuotaCommandService(
                    (rule, key) -> RateLimitDecision.unavailable(rule.dimension(), key),
                    (rule, key) -> RateLimitDecision.allowed(rule.dimension(), key, 0), new MicrometerQuotaObserver(registry));
            QuotaCommand command = new QuotaCommand(List.of(new RateLimitPolicy(List.of(RULE), RedisFailureMode.OPEN_WITH_LOCAL)),
                    Map.of(RateLimitDimension.CLIENT, "private-identity"), false);
            HashSet<RateLimitRuleId> applied = new HashSet<>();
            service.acquire(command, applied);
            service.acquire(command, applied);
            assertEquals(1, registry.get("gateway.quota.decisions").tags("decision", "allowed").counter().count());
            assertEquals(1, registry.getMeters().size());
        } finally {
            registry.close();
        }
    }
}
