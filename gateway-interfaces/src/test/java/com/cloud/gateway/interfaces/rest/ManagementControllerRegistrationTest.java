package com.cloud.gateway.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Definition writes now use a real Controller marker and are excluded from the boot scan.
 * They remain owned by conditional PolicyAdminAutoConfiguration. Other management adapters
 * retain their prior annotations; annotation shape alone does not prove HTTP reachability.
 */
class ManagementControllerRegistrationTest {
    private static final List<Class<?>> CONTROLLERS = List.of(
            GatewayAccessDefinitionCommandController.class,
            GatewayAccessDefinitionQueryController.class,
            GatewayAccessSnapshotCommandController.class,
            GatewayAccessSnapshotQueryController.class);

    @Test
    void shouldNotBeComponentCandidates() {
        CONTROLLERS.stream().filter(type -> type != GatewayAccessDefinitionCommandController.class).forEach(type -> assertFalse(
                AnnotatedElementUtils.hasAnnotation(type, Component.class),
                type.getSimpleName() + " must not be component-scannable"));
    }

    @Test
    void shouldRemainRequestHandlerCandidates() {
        CONTROLLERS.forEach(type -> {
            assertTrue(AnnotatedElementUtils.hasAnnotation(type, RequestMapping.class)
                            || AnnotatedElementUtils.hasAnnotation(type, Controller.class),
                    type.getSimpleName() + " must be a handler candidate");
            assertTrue(AnnotatedElementUtils.hasAnnotation(type, ResponseBody.class),
                    type.getSimpleName() + " must serialise its return value");
        });
    }
}
