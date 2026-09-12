package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.framework.core.naming.NamespaceResolver;
import com.cloud.gateway.domain.policy.capability.RateLimitQuota;
import com.cloud.gateway.application.quota.LocalRateLimitQuota;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.application.quota.QuotaObserver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitRedisAutoConfigurationTest {
    @Test
    void shouldRejectUnboundedFallbackConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RateLimitRedisAutoConfiguration.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(NamespaceResolver.class, () -> namespaced -> "gateway")
                .withPropertyValues("gateway.ratelimit.redis.local-max-buckets=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldAssembleSynchronousQuotaWithFrameworkNamespaceResolver() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RateLimitRedisAutoConfiguration.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(NamespaceResolver.class, () -> namespaced -> "gateway")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RateLimitQuota.class);
                    assertThat(context.getBean(RateLimitQuota.class)).isInstanceOf(RateLimitQuotaAdapter.class);
                    assertThat(context).hasSingleBean(LocalRateLimitQuota.class);
                    assertThat(context).hasSingleBean(QuotaCommandService.class);
                    assertThat(context).hasSingleBean(QuotaObserver.class);
                });
    }
}
