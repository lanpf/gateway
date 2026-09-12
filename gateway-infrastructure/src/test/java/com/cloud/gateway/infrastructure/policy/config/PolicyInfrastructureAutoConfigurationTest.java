package com.cloud.gateway.infrastructure.policy.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloud.gateway.application.policy.AppPolicyCommandService;
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
                    "gateway.policy-definition.endpoints[0].endpoint-endpointId", "TEST.ENDPOINT",
                    "gateway.policy-definition.endpoints[0].service-endpointId", "test-service",
                    "gateway.policy-definition.endpoints[0].matchers[0].http-method", "GET",
                    "gateway.policy-definition.endpoints[0].matchers[0].path-pattern", "/test",
                    "gateway.policy-definition.endpoints[0].route-endpointId", "test-route",
                    "gateway.policy-definition.apps[app].status", "ENABLED",
                    "gateway.policy-definition.apps[app].platform", "WEB",
                    "gateway.policy-definition.routes[TEST.ENDPOINT].status", "ENABLED"
            )));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
            context.register(PolicyInfrastructureAutoConfiguration.class);

            context.refresh();

            assertNotNull(context.getBean(PolicySnapshotProvider.class));
            assertNull(context.getBeanProvider(PolicySnapshotPublisher.class).getIfAvailable());
            assertNull(context.getBeanProvider(PolicyPublishLock.class).getIfAvailable());
            assertNull(context.getBeanProvider(AppPolicyCommandService.class).getIfAvailable());
            assertNull(context.getBeanProvider(PolicySnapshotCommandService.class).getIfAvailable());
            assertNotNull(context.getBean(com.cloud.gateway.application.policy.PolicyCompositionDefinitionRepository.class));
            assertNull(context.getBeanProvider(com.cloud.gateway.application.policy.WritablePolicyCompositionDefinitionRepository.class).getIfAvailable());
            assertNull(context.getBeanProvider(com.cloud.gateway.application.policy.PolicyCompositionCommandService.class).getIfAvailable());
        }
    }
}
