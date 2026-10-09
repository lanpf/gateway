package com.cloud.gateway.infrastructure.persistence.nacos.config;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.infrastructure.policy.config.AccessOperationProperties;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NacosBlockingLimitsTest {
    @Test
    void shouldRejectUnboundedSdkAndReadBudgetsBeforeCreatingTheAdapter() {
        var operations = new AccessOperationProperties();
        var policy = new NacosPolicyProperties();
        assertDoesNotThrow(() -> NacosPolicyAutoConfiguration.validateBlockingLimits(sdk(5000), policy, operations));
        assertThrows(FrameworkException.class,
                () -> NacosPolicyAutoConfiguration.validateBlockingLimits(sdk(-1), policy, operations));
        assertThrows(FrameworkException.class,
                () -> NacosPolicyAutoConfiguration.validateBlockingLimits(sdk(6000), policy, operations));
        policy.setReadTimeout(Duration.ofSeconds(6));
        assertThrows(FrameworkException.class,
                () -> NacosPolicyAutoConfiguration.validateBlockingLimits(sdk(5000), policy, operations));
    }

    private static NacosConfigProperties sdk(long timeout) {
        return new NacosConfigProperties() {
            @Override public Properties assembleConfigServiceProperties() {
                Properties properties = new Properties();
                properties.setProperty(PropertyKeyConst.CONFIG_REQUEST_TIMEOUT, Long.toString(timeout));
                return properties;
            }
        };
    }
}
