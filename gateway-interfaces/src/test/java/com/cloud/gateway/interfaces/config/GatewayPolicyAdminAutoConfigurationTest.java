package com.cloud.gateway.interfaces.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloud.gateway.api.facade.GatewayPolicyCommandFacade;
import com.cloud.gateway.application.policy.ChannelAccessPolicyCommandService;
import com.cloud.gateway.application.policy.ChannelCredentialPolicyCommandService;
import com.cloud.gateway.application.policy.ClientAppPolicyCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.application.policy.RouteAccessPolicyCommandService;
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

            assertNull(context.getBeanProvider(GatewayPolicyCommandFacade.class).getIfAvailable());
            assertNull(context.getBeanProvider(GatewayPolicyAdminController.class).getIfAvailable());
        }
    }

    @Test
    void shouldExposeAdminAdaptersWhenEveryApplicationCommandIsAvailable() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ClientAppPolicyCommandService.class,
                    () -> new ClientAppPolicyCommandService(null, null, null));
            context.registerBean(ChannelAccessPolicyCommandService.class,
                    () -> new ChannelAccessPolicyCommandService(null, null, null));
            context.registerBean(ChannelCredentialPolicyCommandService.class,
                    () -> new ChannelCredentialPolicyCommandService(null, null, null));
            context.registerBean(RouteAccessPolicyCommandService.class,
                    () -> new RouteAccessPolicyCommandService(null, null, null));
            context.registerBean(PolicySnapshotCommandService.class,
                    () -> new PolicySnapshotCommandService(null, null, null, null, null, null, null,
                            null, null, null, null, null, null));
            context.registerBean(GatewayPolicyApiMapper.class, GatewayPolicyAdminAutoConfigurationTest::mapper);
            context.register(GatewayPolicyAdminAutoConfiguration.class);

            context.refresh();

            assertNotNull(context.getBean(GatewayPolicyCommandFacade.class));
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
