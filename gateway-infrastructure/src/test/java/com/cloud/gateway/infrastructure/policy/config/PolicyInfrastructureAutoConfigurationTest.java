package com.cloud.gateway.infrastructure.policy.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloud.gateway.application.configuration.definition.CapabilityCompositionDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableCapabilityCompositionDefinitionRepository;
import com.cloud.gateway.application.configuration.editing.AppAccessCommandService;
import com.cloud.gateway.application.configuration.editing.CapabilityCompositionCommandService;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class PolicyInfrastructureAutoConfigurationTest {
    @Test
    void shouldExposeOnlyReadCapabilitiesWithoutPersistenceAndCoordinationAdapters() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("access-test", Map.of(
                    "gateway.access-definition.endpoints[0].endpoint-id", "TEST.ENDPOINT",
                    "gateway.access-definition.endpoints[0].service-id", "test-service",
                    "gateway.access-definition.endpoints[0].route-id", "test-route",
                    "gateway.access-definition.endpoints[0].matchers[0].http-method", "GET",
                    "gateway.access-definition.endpoints[0].matchers[0].path-pattern", "/test",
                    "gateway.access-definition.endpoints[0].access.client-verification-requirement", "REQUIRED",
                    "gateway.access-definition.endpoints[0].access.subject-authentication-requirement", "ANONYMOUS",
                    "gateway.access-definition.apps[app].status", "ENABLED",
                    "gateway.access-definition.apps[app].platform", "WEB"
            )));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
            context.register(PolicyInfrastructureAutoConfiguration.class);
            context.scan("com.cloud.gateway.infrastructure.policy.mapstruct");

            context.refresh();

            for (Class<?> contract : java.util.List.of(
                    com.cloud.gateway.infrastructure.policy.AppPropertiesMapper.class,
                    com.cloud.gateway.infrastructure.policy.ClientPropertiesMapper.class,
                    com.cloud.gateway.infrastructure.policy.EndpointPropertiesMapper.class,
                    com.cloud.gateway.infrastructure.policy.CapabilityCompositionPropertiesMapper.class,
                    com.cloud.gateway.infrastructure.policy.CapabilityCompositionStorageMapper.class,
                    com.cloud.gateway.infrastructure.policy.AccessSnapshotDocumentMapper.class,
                    com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper.class,
                    com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper.class)) {
                assertEquals(1, context.getBeansOfType(contract).size(), contract.getSimpleName());
            }
            var snapshot = context.getBean(AccessSnapshotCapturer.class).capture().snapshot();
            var storage = context.getBean(com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper.class);
            assertEquals(snapshot.contentHash(), storage.toDomain(storage.toStore(snapshot)).contentHash());
            assertEquals(snapshot.contentHash(), context.getBean(
                    com.cloud.gateway.application.configuration.publication.AccessSnapshotHasher.class)
                    .hash(storage.toDomain(storage.toStore(snapshot)).content()));
            assertEquals(storage.toStore(snapshot), storage.toStore(storage.toDomain(storage.toStore(snapshot))));

            assertNotNull(context.getBean(AccessSnapshotCapturer.class));
            assertNull(context.getBeanProvider(AccessSnapshotPublisher.class).getIfAvailable());
            assertNull(context.getBeanProvider(AccessPublishLock.class).getIfAvailable());
            assertNull(context.getBeanProvider(AppAccessCommandService.class).getIfAvailable());
            assertNull(context.getBeanProvider(AccessSnapshotCommandService.class).getIfAvailable());
            assertNotNull(context.getBean(CapabilityCompositionDefinitionRepository.class));
            assertNull(context.getBeanProvider(WritableCapabilityCompositionDefinitionRepository.class).getIfAvailable());
            assertNull(context.getBeanProvider(CapabilityCompositionCommandService.class).getIfAvailable());
        }
    }
}
