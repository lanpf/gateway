package com.cloud.gateway.interfaces.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloud.gateway.api.facade.IngressAccessCommandFacade;
import com.cloud.gateway.application.policy.ClientAccessPolicyCommandService;
import com.cloud.gateway.application.policy.ClientCredentialPolicyCommandService;
import com.cloud.gateway.application.policy.AppPolicyCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.application.policy.EndpointAccessPolicyCommandService;
import com.cloud.gateway.interfaces.facade.mapper.GatewayPolicyApiMapper;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminController;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class GatewayPolicyAdminAutoConfigurationTest {
    @Test
    void shouldNotExposeAdminAdaptersWithoutApplicationWriteCapabilities() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(GatewayPolicyAdminAutoConfiguration.class);

            context.refresh();

            assertNull(context.getBeanProvider(IngressAccessCommandFacade.class).getIfAvailable());
            assertNull(context.getBeanProvider(GatewayPolicyAdminController.class).getIfAvailable());
        }
    }

    @Test
    void shouldExposeAdminAdaptersWhenEveryApplicationCommandIsAvailable() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AppPolicyCommandService.class,
                    () -> new AppPolicyCommandService(null, null, null));
            context.registerBean(ClientAccessPolicyCommandService.class,
                    () -> new ClientAccessPolicyCommandService(null, null, null));
            context.registerBean(ClientCredentialPolicyCommandService.class,
                    () -> new ClientCredentialPolicyCommandService(null, null, null));
            context.registerBean(EndpointAccessPolicyCommandService.class,
                    () -> new EndpointAccessPolicyCommandService(null, null, null));
            context.registerBean(com.cloud.gateway.application.policy.PolicyCompositionCommandService.class,
                    () -> new com.cloud.gateway.application.policy.PolicyCompositionCommandService(null, null, null,
                            com.cloud.gateway.domain.policy.header.HeaderRules.registeredOnly()));
            context.registerBean(PolicySnapshotCommandService.class,
                    () -> new PolicySnapshotCommandService(null, null));
            context.registerBean(GatewayPolicyApiMapper.class, GatewayPolicyAdminAutoConfigurationTest::mapper);
            context.register(GatewayPolicyAdminAutoConfiguration.class);

            context.refresh();

            assertNotNull(context.getBean(IngressAccessCommandFacade.class));
            assertNotNull(context.getBean(GatewayPolicyAdminController.class));
        }
    }

    private static GatewayPolicyApiMapper mapper() {
        return (GatewayPolicyApiMapper) Proxy.newProxyInstance(
                GatewayPolicyApiMapper.class.getClassLoader(),
                new Class<?>[]{GatewayPolicyApiMapper.class},
                (proxy, method, arguments) -> null);
    }
}
