package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.api.facade.IngressAccessCommandFacade;
import com.cloud.gateway.application.policy.AppPolicyCommandService;
import com.cloud.gateway.application.policy.ClientAccessPolicyCommandService;
import com.cloud.gateway.application.policy.ClientCredentialPolicyCommandService;
import com.cloud.gateway.application.policy.EndpointAccessPolicyCommandService;
import com.cloud.gateway.application.policy.PolicyCompositionCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.interfaces.facade.DefaultIngressAccessCommandFacade;
import com.cloud.gateway.interfaces.facade.mapper.DefaultPolicyCompositionApiMapper;
import com.cloud.gateway.interfaces.facade.mapper.GatewayPolicyApiMapper;
import com.cloud.gateway.interfaces.facade.mapper.PolicyCompositionApiMapper;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminController;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminRestMapper;
import com.cloud.gateway.interfaces.rest.PolicyCompositionRestMapper;
import com.cloud.gateway.interfaces.rest.mapstruct.GatewayPolicyAdminRestMapStructMapper;
import com.cloud.gateway.interfaces.rest.mapstruct.PolicyCompositionRestMapStructMapper;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "com.cloud.gateway.infrastructure.policy.config.PolicyInfrastructureAutoConfiguration")
@ConditionalOnBean({
        AppPolicyCommandService.class,
        ClientAccessPolicyCommandService.class,
        ClientCredentialPolicyCommandService.class,
        EndpointAccessPolicyCommandService.class,
        PolicySnapshotCommandService.class,
        PolicyCompositionCommandService.class
})
public class GatewayPolicyAdminAutoConfiguration {
    // 生成实现类在 boot 应用中由组件扫描注册；这里的 Bean 仅为无扫描的上下文提供实例。
    @Bean @ConditionalOnMissingBean(GatewayPolicyAdminRestMapper.class)
    GatewayPolicyAdminRestMapper gatewayPolicyAdminRestMapper() {
        return Mappers.getMapper(GatewayPolicyAdminRestMapStructMapper.class);
    }

    @Bean @ConditionalOnMissingBean
    PolicyCompositionApiMapper policyConfigurationApiMapper() {
        return new DefaultPolicyCompositionApiMapper();
    }

    @Bean @ConditionalOnMissingBean(PolicyCompositionRestMapper.class)
    PolicyCompositionRestMapper policyConfigurationRestMapper() {
        return Mappers.getMapper(PolicyCompositionRestMapStructMapper.class);
    }

    @Bean
    @ConditionalOnMissingBean(IngressAccessCommandFacade.class)
    IngressAccessCommandFacade gatewayPolicyCommandFacade(
            AppPolicyCommandService appService,
            ClientAccessPolicyCommandService clientAccessService,
            ClientCredentialPolicyCommandService clientCredentialService,
            EndpointAccessPolicyCommandService endpointAccessService,
            PolicySnapshotCommandService snapshotService,
            GatewayPolicyApiMapper mapper,
            PolicyCompositionCommandService policies,
            PolicyCompositionApiMapper policyMapper
    ) {
        return new DefaultIngressAccessCommandFacade(appService, clientAccessService,
                clientCredentialService, endpointAccessService, snapshotService, mapper, policies, policyMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayPolicyAdminController gatewayPolicyAdminController(IngressAccessCommandFacade facade,
                                                              GatewayPolicyAdminRestMapper adminMapper,
                                                              PolicyCompositionRestMapper mapper) {
        return new GatewayPolicyAdminController(facade, adminMapper, mapper);
    }
}
