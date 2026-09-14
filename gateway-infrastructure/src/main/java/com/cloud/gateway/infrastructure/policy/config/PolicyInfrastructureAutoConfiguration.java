package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.policy.header.HeaderRules;

import com.cloud.gateway.application.access.definition.PolicyConfigDefinitionRepository;
import com.cloud.gateway.application.access.definition.WritablePolicyConfigDefinitionRepository;
import com.cloud.gateway.application.access.publication.PolicyConfigCommandService;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesPolicyConfigDefinitionRepository;

import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.application.access.definition.ClientAccessDefinitionRepository;
import com.cloud.gateway.application.access.editing.ClientAccessCommandService;
import com.cloud.gateway.application.access.editing.ClientCredentialCommandService;
import com.cloud.gateway.application.access.definition.AppAccessDefinitionRepository;
import com.cloud.gateway.application.access.editing.AppAccessCommandService;
import com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository;
import com.cloud.gateway.application.access.AccessPublishLock;
import com.cloud.gateway.application.access.publication.AccessRollbackLock;
import com.cloud.gateway.application.access.publication.AccessRollbackState;
import com.cloud.gateway.application.access.publication.AccessSnapshotCommandService;
import com.cloud.gateway.application.access.publication.AccessDefinitions;
import com.cloud.gateway.application.access.definition.AccessDefinitionReader;
import com.cloud.gateway.application.access.publication.AccessSnapshotAssembler;
import com.cloud.gateway.application.access.publication.AccessSnapshotPublication;
import com.cloud.gateway.application.access.publication.AccessPublicationCommandService;
import com.cloud.gateway.application.access.publication.AccessRollbackCommandService;
import com.cloud.gateway.application.access.publication.AccessSnapshotHasher;
import com.cloud.gateway.application.access.publication.AccessSnapshotHistory;
import com.cloud.gateway.application.access.publication.AccessSnapshotProvider;
import com.cloud.gateway.application.access.publication.AccessSnapshotPublisher;
import com.cloud.gateway.application.access.editing.EndpointAccessCommandService;
import com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository;
import com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository;
import com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.ClientPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.ClientPropertiesMapStructMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.PolicyConfigPropertiesMapStructMapper;
import com.cloud.gateway.infrastructure.policy.PolicyConfigPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesClientAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesAppAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesEndpointAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesAccessSnapshotProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.mapstruct.factory.Mappers;

@AutoConfiguration
@AutoConfigureAfter(name = {
        "com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyAutoConfiguration",
        "com.cloud.gateway.infrastructure.coordination.redis.config.RedisPolicyCoordinationAutoConfiguration"
})
@EnableConfigurationProperties({GatewayPolicyDefinitionProperties.class, GatewayHeaderProperties.class})
public class PolicyInfrastructureAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ClientPropertiesMapper clientPropertiesMapper() {
        return Mappers.getMapper(ClientPropertiesMapStructMapper.class);
    }

    @Bean
    @ConditionalOnMissingBean
    PolicyConfigPropertiesMapper policyCompositionPropertiesMapper() {
        return Mappers.getMapper(PolicyConfigPropertiesMapStructMapper.class);
    }

    @Bean
    @ConditionalOnMissingBean
    HeaderRules headerRules(GatewayHeaderProperties properties) {
        return properties.toRules();
    }
    @Bean
    @ConditionalOnMissingBean
    Clock gatewayClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    CanonicalPolicyContentMapper canonicalPolicyContentMapper() {
        return new CanonicalPolicyContentMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    com.cloud.gateway.application.access.publication.AccessSnapshotHasher policySnapshotHasher(
            CanonicalPolicyContentMapper mapper,
            ObjectMapper objectMapper
    ) {
        return new CanonicalAccessSnapshotHasher(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    StoredAccessSnapshotMapper storedAccessSnapshotMapper() {
        return new StoredAccessSnapshotMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    StoredAccessSnapshotCodec storedAccessSnapshotCodec(
            StoredAccessSnapshotMapper mapper,
            com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher,
            ObjectMapper objectMapper,
            HeaderRules headerRules
    ) {
        return new StoredAccessSnapshotCodec(mapper, hasher, objectMapper, headerRules);
    }

    @Bean
    @ConditionalOnMissingBean
    StoredPolicyDefinitionCodec storedPolicyDefinitionCodec(
            StoredAccessSnapshotMapper mapper,
            ObjectMapper objectMapper,
            HeaderRules headerRules
    ) {
        return new StoredPolicyDefinitionCodec(mapper, objectMapper, headerRules);
    }

    @Bean
    @ConditionalOnMissingBean
    com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository endpointRegistryDefinitionRepository(
            GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec
    ) {
        return new PropertiesEndpointAccessDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    com.cloud.gateway.application.access.definition.AppAccessDefinitionRepository appAccessDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredAccessSnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec) {
        return new PropertiesAppAccessDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    com.cloud.gateway.application.access.definition.ClientAccessDefinitionRepository clientAccessDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredAccessSnapshotMapper mapper,
            ClientPropertiesMapper clients, StoredPolicyDefinitionCodec codec) {
        return new PropertiesClientAccessDefinitionRepository(properties, mapper, clients, codec);
    }

    @Bean @ConditionalOnMissingBean
    com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository endpointAccessAccessDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredAccessSnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec) {
        return new PropertiesEndpointAccessDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    com.cloud.gateway.application.access.definition.PolicyConfigDefinitionRepository policyConfigurationDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredAccessSnapshotMapper mapper,
            PolicyConfigPropertiesMapper policyConfiguration, StoredPolicyDefinitionCodec codec) {
        return new PropertiesPolicyConfigDefinitionRepository(properties, mapper, policyConfiguration, codec);
    }

    @Bean @ConditionalOnMissingBean
    @ConditionalOnBean({com.cloud.gateway.application.access.definition.WritablePolicyConfigDefinitionRepository.class, com.cloud.gateway.application.access.publication.AccessRollbackState.class, AccessPublishLock.class})
    com.cloud.gateway.application.access.publication.PolicyConfigCommandService policyConfigurationCommandService(
            com.cloud.gateway.application.access.definition.WritablePolicyConfigDefinitionRepository repository, com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState, AccessPublishLock publishLock,
            HeaderRules headerRules) {
        return new com.cloud.gateway.application.access.publication.PolicyConfigCommandService(repository, rollbackState, publishLock, headerRules);
    }

    @Bean
    @ConditionalOnMissingBean
    com.cloud.gateway.application.access.publication.AccessSnapshotProvider policySnapshotProvider(
            com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository endpointRegistry,
            com.cloud.gateway.application.access.definition.AppAccessDefinitionRepository apps,
            com.cloud.gateway.application.access.definition.ClientAccessDefinitionRepository clientAccess,
            com.cloud.gateway.application.access.definition.EndpointAccessDefinitionRepository endpointAccess,
            com.cloud.gateway.application.access.definition.PolicyConfigDefinitionRepository policies,
            com.cloud.gateway.application.access.publication.AccessSnapshotAssembler assembler,
            Clock clock
    ) {
        return new PropertiesAccessSnapshotProvider(new com.cloud.gateway.application.access.definition.AccessDefinitionReader(endpointRegistry, apps, clientAccess,
                endpointAccess, policies), assembler, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    com.cloud.gateway.application.access.publication.AccessSnapshotAssembler policySnapshotAssembler(com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher, HeaderRules headerRules) {
        return new com.cloud.gateway.application.access.publication.AccessSnapshotAssembler(hasher, headerRules);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LockExecutor.class)
    AccessPublishLock policyPublishLock(LockExecutor lockExecutor) {
        return new AccessPublishLock(lockExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LockExecutor.class)
    com.cloud.gateway.application.access.publication.AccessRollbackLock policyRollbackLock(LockExecutor lockExecutor) {
        return new com.cloud.gateway.application.access.publication.AccessRollbackLock(lockExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository.class,
            com.cloud.gateway.application.access.publication.AccessRollbackState.class, AccessPublishLock.class})
    com.cloud.gateway.application.access.editing.AppAccessCommandService appPolicyCommandService(
            com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository repository,
            com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState,
            AccessPublishLock publishLock
    ) {
        return new com.cloud.gateway.application.access.editing.AppAccessCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository.class,
            com.cloud.gateway.application.access.publication.AccessRollbackState.class, AccessPublishLock.class})
    com.cloud.gateway.application.access.editing.ClientAccessCommandService clientAccessPolicyCommandService(
            com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository repository,
            com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState,
            AccessPublishLock publishLock
    ) {
        return new com.cloud.gateway.application.access.editing.ClientAccessCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository.class,
            com.cloud.gateway.application.access.publication.AccessRollbackState.class, AccessPublishLock.class})
    com.cloud.gateway.application.access.editing.ClientCredentialCommandService clientCredentialPolicyCommandService(
            com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository repository,
            com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState,
            AccessPublishLock publishLock
    ) {
        return new com.cloud.gateway.application.access.editing.ClientCredentialCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository.class,
            com.cloud.gateway.application.access.publication.AccessRollbackState.class, AccessPublishLock.class})
    com.cloud.gateway.application.access.editing.EndpointAccessCommandService endpointAccessPolicyCommandService(
            com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository repository,
            com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState,
            AccessPublishLock publishLock
    ) {
        return new com.cloud.gateway.application.access.editing.EndpointAccessCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository.class,
            com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository.class,
            com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository.class,
            com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository.class,
            com.cloud.gateway.application.access.definition.WritablePolicyConfigDefinitionRepository.class,
            com.cloud.gateway.application.access.publication.AccessSnapshotPublisher.class,
            com.cloud.gateway.application.access.publication.AccessSnapshotHistory.class,
            com.cloud.gateway.application.access.publication.AccessRollbackState.class,
            AccessPublishLock.class,
            com.cloud.gateway.application.access.publication.AccessRollbackLock.class
    })
    com.cloud.gateway.application.access.publication.AccessSnapshotCommandService policySnapshotCommandService(
            com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository endpointRegistry,
            com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository apps,
            com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository clientAccess,
            com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository endpointAccess,
            com.cloud.gateway.application.access.definition.WritablePolicyConfigDefinitionRepository policies,
            com.cloud.gateway.application.access.publication.AccessSnapshotProvider provider,
            com.cloud.gateway.application.access.publication.AccessSnapshotPublisher publisher,
            com.cloud.gateway.application.access.publication.AccessSnapshotHistory history,
            com.cloud.gateway.application.access.publication.AccessRollbackState rollbackState,
            com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher,
            AccessPublishLock publishLock,
            com.cloud.gateway.application.access.publication.AccessRollbackLock rollbackLock,
            Clock clock,
            com.cloud.gateway.application.access.publication.AccessSnapshotAssembler assembler
    ) {
        com.cloud.gateway.application.access.publication.AccessDefinitions definitions = new com.cloud.gateway.application.access.publication.AccessDefinitions(
                endpointRegistry, apps, clientAccess, endpointAccess, policies);
        com.cloud.gateway.application.access.publication.AccessSnapshotPublication publication = new com.cloud.gateway.application.access.publication.AccessSnapshotPublication(publisher, assembler, clock);
        return new com.cloud.gateway.application.access.publication.AccessSnapshotCommandService(
                new com.cloud.gateway.application.access.publication.AccessPublicationCommandService(definitions, provider, publication, rollbackState,
                        publishLock),
                new com.cloud.gateway.application.access.publication.AccessRollbackCommandService(definitions, provider, publication, history, rollbackState,
                        hasher, publishLock, rollbackLock));
    }
}
