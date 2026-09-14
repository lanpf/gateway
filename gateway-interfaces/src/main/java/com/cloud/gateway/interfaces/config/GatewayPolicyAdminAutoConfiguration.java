package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.api.facade.admin.definition.IngressAccessDefinitionCommandFacade;
import com.cloud.gateway.api.facade.admin.definition.IngressAccessDefinitionQueryFacade;
import com.cloud.gateway.api.facade.admin.snapshot.IngressAccessSnapshotCommandFacade;
import com.cloud.gateway.api.facade.admin.snapshot.IngressAccessSnapshotQueryFacade;
import com.cloud.gateway.application.access.definition.AccessDefinitionReader;
import com.cloud.gateway.application.access.publication.AccessSnapshotCommandService;
import com.cloud.gateway.application.access.publication.AccessSnapshotProvider;
import com.cloud.gateway.application.access.editing.AppAccessCommandService;
import com.cloud.gateway.application.access.definition.AppAccessDefinitionRepository;
import com.cloud.gateway.application.access.definition.ClientAccessDefinitionRepository;
import com.cloud.gateway.application.access.editing.ClientAccessCommandService;
import com.cloud.gateway.application.access.editing.ClientCredentialCommandService;
import com.cloud.gateway.application.access.editing.EndpointAccessCommandService;
import com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository;
import com.cloud.gateway.interfaces.facade.admin.definition.DefaultIngressAccessDefinitionCommandFacade;
import com.cloud.gateway.interfaces.facade.admin.definition.DefaultIngressAccessDefinitionQueryFacade;
import com.cloud.gateway.interfaces.facade.admin.snapshot.DefaultIngressAccessSnapshotCommandFacade;
import com.cloud.gateway.interfaces.facade.admin.snapshot.DefaultIngressAccessSnapshotQueryFacade;
import com.cloud.gateway.interfaces.rest.GatewayAccessDefinitionCommandController;
import com.cloud.gateway.interfaces.rest.GatewayAccessDefinitionQueryController;
import com.cloud.gateway.interfaces.rest.GatewayAccessSnapshotCommandController;
import com.cloud.gateway.interfaces.rest.GatewayAccessSnapshotQueryController;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminRestMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Management-surface assembly: query facades exist in every deployment, command facades only when
 * a writable definition store is present (read-only deployments expose the queries alone).
 */
@AutoConfiguration(afterName = "com.cloud.gateway.infrastructure.policy.config.PolicyInfrastructureAutoConfiguration")
public class GatewayPolicyAdminAutoConfiguration {

    @Bean @ConditionalOnMissingBean(GatewayPolicyAdminRestMapper.class)
    GatewayPolicyAdminRestMapper gatewayPolicyAdminRestMapper() {
        return new GatewayPolicyAdminRestMapper() { };
    }

    @Bean @ConditionalOnMissingBean(com.cloud.gateway.application.access.definition.AccessDefinitionReader.class)
    com.cloud.gateway.application.access.definition.AccessDefinitionReader accessDefinitionReader(
            com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository endpoints,
            com.cloud.gateway.application.access.definition.AppAccessDefinitionRepository apps,
            com.cloud.gateway.application.access.definition.ClientAccessDefinitionRepository clients,
            com.cloud.gateway.application.access.definition.PolicyConfigDefinitionRepository policies) {
        return new com.cloud.gateway.application.access.definition.AccessDefinitionReader(endpoints, apps, clients, policies);
    }

    @Bean @ConditionalOnMissingBean(IngressAccessDefinitionQueryFacade.class)
    IngressAccessDefinitionQueryFacade ingressAccessDefinitionQueryFacade(com.cloud.gateway.application.access.definition.AccessDefinitionReader reader) {
        return new DefaultIngressAccessDefinitionQueryFacade(reader);
    }

    @Bean @ConditionalOnMissingBean(IngressAccessSnapshotQueryFacade.class)
    IngressAccessSnapshotQueryFacade ingressAccessSnapshotQueryFacade(com.cloud.gateway.application.access.publication.AccessSnapshotProvider provider) {
        return new DefaultIngressAccessSnapshotQueryFacade(provider);
    }

    @Bean
    @ConditionalOnBean({com.cloud.gateway.application.access.editing.AppAccessCommandService.class, com.cloud.gateway.application.access.editing.ClientAccessCommandService.class,
            com.cloud.gateway.application.access.editing.ClientCredentialCommandService.class, com.cloud.gateway.application.access.editing.EndpointAccessCommandService.class})
    @ConditionalOnMissingBean(IngressAccessDefinitionCommandFacade.class)
    IngressAccessDefinitionCommandFacade ingressAccessDefinitionCommandFacade(
            com.cloud.gateway.application.access.editing.AppAccessCommandService appService,
            com.cloud.gateway.application.access.editing.ClientAccessCommandService clientAccessService,
            com.cloud.gateway.application.access.editing.ClientCredentialCommandService clientCredentialService,
            com.cloud.gateway.application.access.editing.EndpointAccessCommandService endpointAccessService) {
        return new DefaultIngressAccessDefinitionCommandFacade(appService, clientAccessService,
                clientCredentialService, endpointAccessService);
    }

    @Bean
    @ConditionalOnBean(com.cloud.gateway.application.access.publication.AccessSnapshotCommandService.class)
    @ConditionalOnMissingBean(IngressAccessSnapshotCommandFacade.class)
    IngressAccessSnapshotCommandFacade ingressAccessSnapshotCommandFacade(
            com.cloud.gateway.application.access.publication.AccessSnapshotCommandService snapshotService) {
        return new DefaultIngressAccessSnapshotCommandFacade(snapshotService);
    }

    @Bean
    @ConditionalOnBean(IngressAccessDefinitionCommandFacade.class)
    GatewayAccessDefinitionCommandController gatewayAccessDefinitionCommandController(
            IngressAccessDefinitionCommandFacade facade, GatewayPolicyAdminRestMapper mapper) {
        return new GatewayAccessDefinitionCommandController(facade, mapper);
    }

    @Bean
    GatewayAccessDefinitionQueryController gatewayAccessDefinitionQueryController(
            IngressAccessDefinitionQueryFacade queryFacade) {
        return new GatewayAccessDefinitionQueryController(queryFacade);
    }

    @Bean
    @ConditionalOnBean(IngressAccessSnapshotCommandFacade.class)
    GatewayAccessSnapshotCommandController gatewayAccessSnapshotCommandController(
            IngressAccessSnapshotCommandFacade facade, GatewayPolicyAdminRestMapper mapper) {
        return new GatewayAccessSnapshotCommandController(facade, mapper);
    }

    @Bean
    GatewayAccessSnapshotQueryController gatewayAccessSnapshotQueryController(
            IngressAccessSnapshotQueryFacade queryFacade) {
        return new GatewayAccessSnapshotQueryController(queryFacade);
    }
}
