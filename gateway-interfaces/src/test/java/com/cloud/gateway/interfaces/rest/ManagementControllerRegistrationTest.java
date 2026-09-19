package com.cloud.gateway.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The management controllers are registered by {@code GatewayPolicyAdminAutoConfiguration} only.
 * While they carried {@code @RestController} they were also component-scanned, which registered
 * them before their conditions could be evaluated and silently dropped the conditional ones.
 */
class ManagementControllerRegistrationTest {
    private static final List<Class<?>> CONTROLLERS = List.of(
            GatewayAccessDefinitionCommandController.class,
            GatewayAccessDefinitionQueryController.class,
            GatewayAccessSnapshotCommandController.class,
            GatewayAccessSnapshotQueryController.class);

    @Test
    void shouldNotBeComponentCandidates() {
        CONTROLLERS.forEach(type -> assertFalse(
                AnnotatedElementUtils.hasAnnotation(type, Component.class),
                type.getSimpleName() + " must not be component-scannable"));
    }

    @Test
    void shouldRemainRequestHandlerCandidates() {
        CONTROLLERS.forEach(type -> {
            assertTrue(AnnotatedElementUtils.hasAnnotation(type, RequestMapping.class),
                    type.getSimpleName() + " must be a handler candidate");
            assertTrue(AnnotatedElementUtils.hasAnnotation(type, ResponseBody.class),
                    type.getSimpleName() + " must serialise its return value");
        });
    }
}
