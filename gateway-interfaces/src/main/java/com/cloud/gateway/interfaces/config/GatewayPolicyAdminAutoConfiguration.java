package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.api.facade.admin.definition.AccessDefinitionCommandFacade;
import com.cloud.gateway.api.facade.admin.definition.AccessDefinitionQueryFacade;
import com.cloud.gateway.api.facade.admin.snapshot.AccessSnapshotCommandFacade;
import com.cloud.gateway.api.facade.admin.snapshot.AccessSnapshotQueryFacade;
import com.cloud.gateway.application.configuration.definition.AccessDefinitionReader;
import com.cloud.gateway.application.configuration.definition.AppAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.CapabilityCompositionDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.ClientAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.EndpointAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.editing.AppAccessCommandService;
import com.cloud.gateway.application.configuration.editing.ClientAccessCommandService;
import com.cloud.gateway.application.configuration.editing.ClientCredentialCommandService;
import com.cloud.gateway.application.configuration.editing.EndpointAccessCommandService;
import com.cloud.gateway.interfaces.facade.admin.definition.DefaultAccessDefinitionCommandFacade;
import com.cloud.gateway.interfaces.facade.admin.definition.DefaultAccessDefinitionQueryFacade;
import com.cloud.gateway.interfaces.facade.admin.snapshot.DefaultAccessSnapshotCommandFacade;
import com.cloud.gateway.interfaces.facade.admin.snapshot.DefaultAccessSnapshotQueryFacade;
import com.cloud.gateway.interfaces.rest.GatewayAccessDefinitionCommandController;
import com.cloud.gateway.interfaces.rest.GatewayAccessDefinitionQueryController;
import com.cloud.gateway.interfaces.rest.GatewayAccessSnapshotCommandController;
import com.cloud.gateway.interfaces.rest.GatewayAccessSnapshotQueryController;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminRestMapper;
import com.cloud.gateway.interfaces.rest.mapstruct.GatewayPolicyAdminRestMapStructMapper;
import com.cloud.gateway.interfaces.facade.admin.definition.AccessDefinitionApiMapper;
import com.cloud.gateway.interfaces.facade.admin.definition.mapstruct.AccessDefinitionApiMapStructMapper;
import com.cloud.gateway.interfaces.facade.admin.snapshot.AccessSnapshotApiMapper;
import com.cloud.gateway.interfaces.facade.admin.snapshot.mapstruct.AccessSnapshotApiMapStructMapper;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Management-surface assembly: query facades exist in every deployment, command facades only when
 * a writable definition store is present (read-only deployments expose the queries alone).
 */
@AutoConfiguration(afterName = "com.cloud.gateway.infrastructure.policy.config.PolicyInfrastructureAutoConfiguration")
@ComponentScan(basePackageClasses = {GatewayPolicyAdminRestMapStructMapper.class,
        AccessDefinitionApiMapStructMapper.class, AccessSnapshotApiMapStructMapper.class})
public class GatewayPolicyAdminAutoConfiguration {

    @Bean @ConditionalOnMissingBean(AccessDefinitionReader.class)
    AccessDefinitionReader accessDefinitionReader(
            EndpointAccessDefinitionRepository endpoints,
            AppAccessDefinitionRepository apps,
            ClientAccessDefinitionRepository clients,
            CapabilityCompositionDefinitionRepository capabilities) {
        return new AccessDefinitionReader(endpoints, apps, clients, capabilities);
    }

    @Bean @ConditionalOnMissingBean(AccessDefinitionQueryFacade.class)
    AccessDefinitionQueryFacade ingressAccessDefinitionQueryFacade(AccessDefinitionReader reader,
            AccessDefinitionApiMapper mapper) {
        return new DefaultAccessDefinitionQueryFacade(reader, mapper);
    }

    @Bean @ConditionalOnMissingBean(AccessSnapshotQueryFacade.class)
    AccessSnapshotQueryFacade ingressAccessSnapshotQueryFacade(com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer provider,
            AccessSnapshotApiMapper mapper) {
        return new DefaultAccessSnapshotQueryFacade(provider, mapper);
    }

    @Bean
    @ConditionalOnBean({AppAccessCommandService.class, ClientAccessCommandService.class,
            ClientCredentialCommandService.class, EndpointAccessCommandService.class})
    @ConditionalOnMissingBean(AccessDefinitionCommandFacade.class)
    AccessDefinitionCommandFacade ingressAccessDefinitionCommandFacade(
            AppAccessCommandService appService,
            ClientAccessCommandService clientAccessService,
            ClientCredentialCommandService clientCredentialService,
            EndpointAccessCommandService endpointAccessService,
            AccessDefinitionApiMapper mapper) {
        return new DefaultAccessDefinitionCommandFacade(appService, clientAccessService,
                clientCredentialService, endpointAccessService, mapper);
    }

    @Bean
    @ConditionalOnBean(com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService.class)
    @ConditionalOnMissingBean(AccessSnapshotCommandFacade.class)
    AccessSnapshotCommandFacade ingressAccessSnapshotCommandFacade(
            com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService snapshotService,
            AccessSnapshotApiMapper mapper) {
        return new DefaultAccessSnapshotCommandFacade(snapshotService, mapper);
    }

    @Bean
    @ConditionalOnBean(AccessDefinitionCommandFacade.class)
    GatewayAccessDefinitionCommandController gatewayAccessDefinitionCommandController(
            AccessDefinitionCommandFacade facade, GatewayPolicyAdminRestMapper mapper) {
        return new GatewayAccessDefinitionCommandController(facade, mapper);
    }

    @Bean
    GatewayAccessDefinitionQueryController gatewayAccessDefinitionQueryController(
            AccessDefinitionQueryFacade queryFacade) {
        return new GatewayAccessDefinitionQueryController(queryFacade);
    }

    @Bean
    @ConditionalOnBean(AccessSnapshotCommandFacade.class)
    GatewayAccessSnapshotCommandController gatewayAccessSnapshotCommandController(
            AccessSnapshotCommandFacade facade, GatewayPolicyAdminRestMapper mapper) {
        return new GatewayAccessSnapshotCommandController(facade, mapper);
    }

    @Bean
    GatewayAccessSnapshotQueryController gatewayAccessSnapshotQueryController(
            AccessSnapshotQueryFacade queryFacade) {
        return new GatewayAccessSnapshotQueryController(queryFacade);
    }
}
