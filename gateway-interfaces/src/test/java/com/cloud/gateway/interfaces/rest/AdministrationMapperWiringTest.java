package com.cloud.gateway.interfaces.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.cloud.gateway.application.configuration.definition.AccessDefinitionReader;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.interfaces.config.PolicyAdminAutoConfiguration;
import com.cloud.gateway.interfaces.facade.admin.definition.AccessDefinitionApiMapper;
import com.cloud.gateway.interfaces.facade.admin.snapshot.AccessSnapshotApiMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.cloud.gateway.application.configuration.editing.*;
import com.cloud.gateway.api.facade.admin.definition.AccessDefinitionCommandFacade;
import com.cloud.gateway.infrastructure.policy.CapabilityConfigCodecs;

class AdministrationMapperWiringTest {
    @Test
    void shouldAssembleWriteControllerOnlyWithAllWritableUseCases() {
        for (boolean includeCapabilities : java.util.List.of(false, true)) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.registerBean(CapabilityConfigCodecs.class, () -> mock(CapabilityConfigCodecs.class));
                context.registerBean(AccessDefinitionReader.class, () -> mock(AccessDefinitionReader.class));
                context.registerBean(AccessSnapshotCapturer.class,
                        () -> () -> { throw new AssertionError("unexpected capture"); });
                // This assembly-only test never calls these use cases or their outbound ports.
                context.registerBean(AppAccessCommandService.class, () -> mock(AppAccessCommandService.class));
                context.registerBean(ClientAccessCommandService.class, () -> mock(ClientAccessCommandService.class));
                context.registerBean(ClientCredentialCommandService.class, () -> mock(ClientCredentialCommandService.class));
                context.registerBean(EndpointAccessCommandService.class, () -> mock(EndpointAccessCommandService.class));
                if (includeCapabilities) {
                    context.registerBean(CapabilityPolicyConfigCommandService.class, () -> mock(CapabilityPolicyConfigCommandService.class));
                }
                context.register(PolicyAdminAutoConfiguration.class);
                context.refresh();
                assertEquals(includeCapabilities ? 1 : 0, context.getBeansOfType(AccessDefinitionCommandFacade.class).size());
                assertEquals(includeCapabilities ? 1 : 0, context.getBeansOfType(GatewayAccessDefinitionCommandController.class).size());
            }
        }
    }

    @Test
    void shouldRegisterOneMapperPerContractWithOverlappingScans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CapabilityConfigCodecs.class, () -> mock(CapabilityConfigCodecs.class));
            context.registerBean(AccessDefinitionReader.class, () -> mock(AccessDefinitionReader.class));
            context.registerBean(AccessSnapshotCapturer.class,
                    () -> () -> { throw new AssertionError("unexpected snapshot capture"); });
            context.scan("com.cloud.gateway.interfaces.rest.mapstruct",
                    "com.cloud.gateway.interfaces.facade.admin.definition.mapstruct",
                    "com.cloud.gateway.interfaces.facade.admin.snapshot.mapstruct");
            context.register(PolicyAdminAutoConfiguration.class);
            context.refresh();
            assertEquals(1, context.getBeansOfType(GatewayPolicyAdminRestMapper.class).size());
            assertEquals(1, context.getBeansOfType(AccessDefinitionApiMapper.class).size());
            assertEquals(1, context.getBeansOfType(AccessSnapshotApiMapper.class).size());
            assertNotNull(context.getBean(GatewayAccessDefinitionQueryController.class));
            assertNotNull(context.getBean(GatewayAccessSnapshotQueryController.class));
            assertTrue(context.getBeansOfType(GatewayAccessDefinitionCommandController.class).isEmpty());
        }
    }
}
