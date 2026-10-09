package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator;

import com.cloud.gateway.application.configuration.editing.CapabilityPolicyConfigCommandService;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesCapabilityPolicyConfigDefinitionRepository;

import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.application.configuration.definition.ClientAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.editing.ClientAccessCommandService;
import com.cloud.gateway.application.configuration.editing.ClientCredentialCommandService;
import com.cloud.gateway.application.configuration.definition.AppAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.editing.AppAccessCommandService;
import com.cloud.gateway.application.configuration.definition.EndpointAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.publication.AccessPublishLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackLock;
import com.cloud.gateway.application.configuration.publication.AccessRollbackState;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCommandService;
import com.cloud.gateway.application.configuration.publication.AccessDefinitions;
import com.cloud.gateway.application.configuration.publication.AccessOperationBudget;
import com.cloud.gateway.application.configuration.definition.AccessDefinitionReader;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotAssembler;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotPublication;
import com.cloud.gateway.application.configuration.publication.AccessPublicationCommandService;
import com.cloud.gateway.application.configuration.publication.AccessRollbackCommandService;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotHasher;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotHistory;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotPublisher;
import com.cloud.gateway.application.configuration.editing.EndpointAccessCommandService;
import com.cloud.gateway.application.configuration.definition.CapabilityPolicyConfigDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableClientAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableAppAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableEndpointAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableCapabilityPolicyConfigDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.CanonicalAccessSnapshotHasher;
import com.cloud.gateway.infrastructure.policy.ClientPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractClientPropertiesMapStructMapper;
import com.cloud.gateway.infrastructure.policy.CapabilityPolicyConfigPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesClientAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesAppAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesEndpointAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesAccessSnapshotCapturer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import com.cloud.gateway.infrastructure.policy.AppPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.EndpointPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.CapabilityPolicyConfigStorageMapper;
import com.cloud.gateway.infrastructure.policy.AccessSnapshotDocumentMapper;
import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.DefaultCanonicalPolicyContentMapper;

@org.springframework.context.annotation.Import(CapabilityCodecConfiguration.class)
@AutoConfiguration
@ComponentScan(basePackageClasses = AbstractClientPropertiesMapStructMapper.class)
@AutoConfigureAfter(name = {
        "com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyAutoConfiguration",
        "com.cloud.gateway.infrastructure.coordination.redis.config.RedisPolicyCoordinationAutoConfiguration"
})
@EnableConfigurationProperties({GatewayPolicyDefinitionProperties.class, HeaderAdmissionProperties.class, AccessOperationProperties.class})
public class PolicyInfrastructureAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    HeaderConfigRule headerRules(HeaderAdmissionProperties properties) {
        return properties.toRules();
    }
    @Bean
    @ConditionalOnMissingBean
    Clock gatewayClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    CanonicalPolicyContentMapper canonicalPolicyContentMapper(CapabilityPolicyConfigStorageMapper policies) {
        return new DefaultCanonicalPolicyContentMapper(policies);
    }

    @Bean
    @ConditionalOnMissingBean
    AccessSnapshotHasher accessSnapshotHasher(
            CanonicalPolicyContentMapper mapper,
            ObjectMapper objectMapper
    ) {
        return new CanonicalAccessSnapshotHasher(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    StoredAccessSnapshotMapper storedAccessSnapshotMapper(CapabilityPolicyConfigStorageMapper policies,
            AccessSnapshotDocumentMapper documents) {
        return new DefaultStoredAccessSnapshotMapper(policies, documents);
    }

    @Bean
    @ConditionalOnMissingBean
    StoredAccessSnapshotCodec storedAccessSnapshotCodec(
            StoredAccessSnapshotMapper mapper,
            AccessSnapshotHasher hasher,
            ObjectMapper objectMapper,
            CapabilityPolicyConfigValidator validator
    ) {
        return new StoredAccessSnapshotCodec(mapper, hasher, objectMapper, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    StoredPolicyDefinitionCodec storedPolicyDefinitionCodec(
            StoredAccessSnapshotMapper mapper,
            ObjectMapper objectMapper,
            CapabilityPolicyConfigValidator validator
    ) {
        return new StoredPolicyDefinitionCodec(mapper, objectMapper, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    EndpointAccessDefinitionRepository endpointAccessDefinitionRepository(
            GatewayPolicyDefinitionProperties properties,
            StoredAccessSnapshotMapper mapper,
            EndpointPropertiesMapper endpoints,
            StoredPolicyDefinitionCodec codec
    ) {
        return new PropertiesEndpointAccessDefinitionRepository(properties, mapper, endpoints, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    AppAccessDefinitionRepository appAccessDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredAccessSnapshotMapper mapper,
            AppPropertiesMapper apps,
            StoredPolicyDefinitionCodec codec) {
        return new PropertiesAppAccessDefinitionRepository(properties, mapper, apps, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    ClientAccessDefinitionRepository clientAccessDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredAccessSnapshotMapper mapper,
            ClientPropertiesMapper clients, StoredPolicyDefinitionCodec codec) {
        return new PropertiesClientAccessDefinitionRepository(properties, mapper, clients, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    CapabilityPolicyConfigDefinitionRepository policyConfigDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredAccessSnapshotMapper mapper,
            CapabilityPolicyConfigPropertiesMapper policyConfiguration, StoredPolicyDefinitionCodec codec) {
        return new PropertiesCapabilityPolicyConfigDefinitionRepository(properties, mapper, policyConfiguration, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableCapabilityPolicyConfigDefinitionRepository.class, AccessRollbackState.class,
            AccessPublishLock.class})
    CapabilityPolicyConfigCommandService policyConfigCommandService(
            WritableCapabilityPolicyConfigDefinitionRepository repository, AccessRollbackState rollbackState,
            AccessPublishLock publishLock, CapabilityPolicyConfigValidator validator) {
        return new CapabilityPolicyConfigCommandService(repository, rollbackState, publishLock, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    AccessSnapshotCapturer accessSnapshotCapturer(
            EndpointAccessDefinitionRepository endpoints,
            AppAccessDefinitionRepository apps,
            ClientAccessDefinitionRepository clients,
            CapabilityPolicyConfigDefinitionRepository policies,
            AccessSnapshotAssembler assembler,
            Clock clock
    ) {
        return new PropertiesAccessSnapshotCapturer(
                new AccessDefinitionReader(endpoints, apps, clients, policies), assembler, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    AccessSnapshotAssembler accessSnapshotAssembler(AccessSnapshotHasher hasher, CapabilityPolicyConfigValidator validator) {
        return new AccessSnapshotAssembler(hasher, validator);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LockExecutor.class)
    AccessPublishLock accessPublishLock(LockExecutor lockExecutor) {
        return new AccessPublishLock(lockExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LockExecutor.class)
    AccessRollbackLock accessRollbackLock(LockExecutor lockExecutor) {
        return new AccessRollbackLock(lockExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableAppAccessDefinitionRepository.class, AccessRollbackState.class,
            AccessPublishLock.class})
    AppAccessCommandService appAccessCommandService(
            WritableAppAccessDefinitionRepository repository,
            AccessRollbackState rollbackState,
            AccessPublishLock publishLock
    ) {
        return new AppAccessCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableClientAccessDefinitionRepository.class, AccessRollbackState.class,
            AccessPublishLock.class})
    ClientAccessCommandService clientAccessCommandService(
            WritableClientAccessDefinitionRepository repository,
            AccessRollbackState rollbackState,
            AccessPublishLock publishLock
    ) {
        return new ClientAccessCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableClientAccessDefinitionRepository.class, AccessRollbackState.class,
            AccessPublishLock.class})
    ClientCredentialCommandService clientCredentialCommandService(
            WritableClientAccessDefinitionRepository repository,
            AccessRollbackState rollbackState,
            AccessPublishLock publishLock
    ) {
        return new ClientCredentialCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableEndpointAccessDefinitionRepository.class, AccessRollbackState.class,
            AccessPublishLock.class})
    EndpointAccessCommandService endpointAccessCommandService(
            WritableEndpointAccessDefinitionRepository repository,
            AccessRollbackState rollbackState,
            AccessPublishLock publishLock
    ) {
        return new EndpointAccessCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            WritableEndpointAccessDefinitionRepository.class,
            WritableAppAccessDefinitionRepository.class,
            WritableClientAccessDefinitionRepository.class,
            WritableCapabilityPolicyConfigDefinitionRepository.class,
            AccessSnapshotPublisher.class,
            AccessSnapshotHistory.class,
            AccessRollbackState.class,
            AccessPublishLock.class,
            AccessRollbackLock.class
    })
    AccessSnapshotCommandService accessSnapshotCommandService(
            WritableEndpointAccessDefinitionRepository endpoints,
            WritableAppAccessDefinitionRepository apps,
            WritableClientAccessDefinitionRepository clients,
            WritableCapabilityPolicyConfigDefinitionRepository policies,
            AccessSnapshotCapturer provider,
            AccessSnapshotPublisher publisher,
            AccessSnapshotHistory history,
            AccessRollbackState rollbackState,
            AccessSnapshotHasher hasher,
            AccessPublishLock publishLock,
            AccessRollbackLock rollbackLock,
            Clock clock,
            AccessSnapshotAssembler assembler,
            AccessOperationProperties operationProperties
    ) {
        AccessDefinitions definitions = new AccessDefinitions(endpoints, apps, clients, policies);
        AccessSnapshotPublication publication =
                new AccessSnapshotPublication(publisher, assembler, clock);
        AccessOperationBudget budget = new AccessOperationBudget(operationProperties.getTimeout(), System::nanoTime);
        return new AccessSnapshotCommandService(
                new AccessPublicationCommandService(definitions, provider, publication, rollbackState,
                        publishLock, budget),
                new AccessRollbackCommandService(definitions, provider, publication, history, rollbackState,
                        hasher, publishLock, rollbackLock, budget));
    }
}
