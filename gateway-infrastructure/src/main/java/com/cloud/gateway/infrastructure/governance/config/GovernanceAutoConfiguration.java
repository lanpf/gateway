package com.cloud.gateway.infrastructure.governance.config;

import com.cloud.gateway.application.governance.DownstreamCircuitBreaker;
import com.cloud.gateway.application.governance.ResponseCache;
import com.cloud.gateway.infrastructure.governance.CaffeineResponseCache;
import com.cloud.gateway.infrastructure.governance.Resilience4jDownstreamCircuitBreaker;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import io.micrometer.core.instrument.MeterRegistry;

@AutoConfiguration
@EnableConfigurationProperties(GovernanceProperties.class)
public class GovernanceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ResponseCache.class)
    public ResponseCache responseCache(GovernanceProperties properties, ObjectProvider<Clock> clocks,
            ObjectProvider<MeterRegistry> registries) {
        return new CaffeineResponseCache(properties.getCacheMaximumWeightBytes(),
                clocks.getIfAvailable(Clock::systemUTC), registries.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(DownstreamCircuitBreaker.class)
    public DownstreamCircuitBreaker downstreamCircuitBreaker(GovernanceProperties properties,
            ObjectProvider<MeterRegistry> registries) {
        return new Resilience4jDownstreamCircuitBreaker(properties.getMaximumCircuits(), registries.getIfAvailable());
    }
}
