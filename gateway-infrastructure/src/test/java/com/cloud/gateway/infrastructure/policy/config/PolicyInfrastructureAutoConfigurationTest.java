package com.cloud.gateway.infrastructure.policy.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloud.gateway.application.policy.ClientAppPolicyCommandService;
import com.cloud.gateway.application.policy.PolicyPublishLock;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.policy.PolicySnapshotPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class PolicyInfrastructureAutoConfigurationTest {
    @Test
    void shouldExposeOnlyReadCapabilitiesWithoutPersistenceAndCoordinationAdapters() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("policy-test", Map.of(
                    "gateway.ingress-policy.endpoints[0].endpoint-id", "TEST.ENDPOINT",
                    "gateway.ingress-policy.endpoints[0].owner-service", "test-service",
                    "gateway.ingress-policy.endpoints[0].matchers[0].http-method", "GET",
                    "gateway.ingress-policy.endpoints[0].matchers[0].path-pattern", "/test",
                    "gateway.ingress-policy.endpoints[0].delivery-route-id", "test-route",
                    "gateway.ingress-policy.client-apps[app].status", "ENABLED",
                    "gateway.ingress-policy.client-apps[app].platform", "WEB",
                    "gateway.ingress-policy.routes[TEST.ENDPOINT].enabled", "true"
            )));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
            context.register(PolicyInfrastructureAutoConfiguration.class);

            context.refresh();

            assertNotNull(context.getBean(PolicySnapshotProvider.class));
            assertNull(context.getBeanProvider(PolicySnapshotPublisher.class).getIfAvailable());
            assertNull(context.getBeanProvider(PolicyPublishLock.class).getIfAvailable());
            assertNull(context.getBeanProvider(ClientAppPolicyCommandService.class).getIfAvailable());
            assertNull(context.getBeanProvider(PolicySnapshotCommandService.class).getIfAvailable());
        }
    }
}
