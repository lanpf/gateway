package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.api.facade.GatewayPolicyCommandFacade;
import com.cloud.gateway.application.policy.ChannelAccessPolicyCommandService;
import com.cloud.gateway.application.policy.ChannelCredentialPolicyCommandService;
import com.cloud.gateway.application.policy.ClientAppPolicyCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.application.policy.RouteAccessPolicyCommandService;
import com.cloud.gateway.interfaces.facade.DefaultGatewayPolicyCommandFacade;
import com.cloud.gateway.interfaces.facade.mapper.GatewayPolicyApiMapper;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "com.cloud.gateway.infrastructure.policy.config.PolicyInfrastructureAutoConfiguration")
@ConditionalOnBean({
        ClientAppPolicyCommandService.class,
        ChannelAccessPolicyCommandService.class,
        ChannelCredentialPolicyCommandService.class,
        RouteAccessPolicyCommandService.class,
        PolicySnapshotCommandService.class
})
public class GatewayPolicyAdminAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(GatewayPolicyCommandFacade.class)
    GatewayPolicyCommandFacade gatewayPolicyCommandFacade(
            ClientAppPolicyCommandService clientAppService,
            ChannelAccessPolicyCommandService channelAccessService,
            ChannelCredentialPolicyCommandService channelCredentialService,
            RouteAccessPolicyCommandService routeAccessService,
            PolicySnapshotCommandService snapshotService,
            GatewayPolicyApiMapper mapper
    ) {
        return new DefaultGatewayPolicyCommandFacade(clientAppService, channelAccessService,
                channelCredentialService, routeAccessService, snapshotService, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayPolicyAdminController gatewayPolicyAdminController(GatewayPolicyCommandFacade facade) {
        return new GatewayPolicyAdminController(facade);
    }
}
