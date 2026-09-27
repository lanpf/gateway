package com.cloud.gateway.interfaces.rest;

import static org.junit.jupiter.api.Assertions.*;
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
import com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs;
import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;
import com.fasterxml.jackson.databind.ObjectMapper;

class AdministrationMapperWiringTest {
    @Test
    void shouldAssembleWriteControllerOnlyWithAllWritableUseCases() {
        for (boolean includeCapabilities : java.util.List.of(false, true)) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.registerBean(CapabilityConfigCodecs.class, () -> CapabilityCodecConfiguration.builtIns(
                        BuiltInCapabilityConfigs.registry(), new CapabilityNodeStorageMapStructMapperImpl(), new ObjectMapper()));
                context.registerBean(AccessDefinitionReader.class, () -> new AccessDefinitionReader(
                        () -> { throw new AssertionError("unexpected read"); },
                        () -> { throw new AssertionError("unexpected read"); },
                        () -> { throw new AssertionError("unexpected read"); },
                        () -> { throw new AssertionError("unexpected read"); }));
                context.registerBean(AccessSnapshotCapturer.class,
                        () -> () -> { throw new AssertionError("unexpected capture"); });
                // This assembly-only test never calls these use cases or their outbound ports.
                context.registerBean(AppAccessCommandService.class, () -> new AppAccessCommandService(null, null, null));
                context.registerBean(ClientAccessCommandService.class, () -> new ClientAccessCommandService(null, null, null));
                context.registerBean(ClientCredentialCommandService.class, () -> new ClientCredentialCommandService(null, null, null));
                context.registerBean(EndpointAccessCommandService.class, () -> new EndpointAccessCommandService(null, null, null));
                if (includeCapabilities) {
                    context.registerBean(CapabilityCompositionCommandService.class, () -> new CapabilityCompositionCommandService(
                            null, null, null, HeaderAdmissionRules.registeredOnly(), BuiltInCapabilityConfigs.registry()));
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
            context.registerBean(com.cloud.gateway.infrastructure.policy.CapabilityConfigCodecs.class,
                    () -> com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(
                            com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(),
                            new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(),
                            new com.fasterxml.jackson.databind.ObjectMapper()));
            context.registerBean(AccessDefinitionReader.class, () -> new AccessDefinitionReader(
                    () -> { throw new AssertionError("unexpected endpoint read"); },
                    () -> { throw new AssertionError("unexpected app read"); },
                    () -> { throw new AssertionError("unexpected client read"); },
                    () -> { throw new AssertionError("unexpected capability read"); }));
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
