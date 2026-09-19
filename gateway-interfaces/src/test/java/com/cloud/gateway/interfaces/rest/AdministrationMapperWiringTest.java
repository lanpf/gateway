package com.cloud.gateway.interfaces.rest;

import static org.junit.jupiter.api.Assertions.*;
import com.cloud.gateway.application.configuration.definition.AccessDefinitionReader;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.interfaces.config.GatewayPolicyAdminAutoConfiguration;
import com.cloud.gateway.interfaces.facade.admin.definition.AccessDefinitionApiMapper;
import com.cloud.gateway.interfaces.facade.admin.snapshot.AccessSnapshotApiMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AdministrationMapperWiringTest {
    @Test
    void shouldRegisterOneMapperPerContractWithOverlappingScans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
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
            context.register(GatewayPolicyAdminAutoConfiguration.class);
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
