package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.policy.header.HeaderRules;

import com.cloud.gateway.application.policy.PolicyCompositionDefinitionRepository;
import com.cloud.gateway.application.policy.WritablePolicyCompositionDefinitionRepository;
import com.cloud.gateway.application.policy.PolicyCompositionCommandService;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesPolicyCompositionDefinitionRepository;

import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.application.policy.ClientPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.ClientAccessPolicyCommandService;
import com.cloud.gateway.application.policy.ClientCredentialPolicyCommandService;
import com.cloud.gateway.application.policy.AppPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.AppPolicyCommandService;
import com.cloud.gateway.application.policy.EndpointRegistryDefinitionRepository;
import com.cloud.gateway.application.policy.PolicyPublishLock;
import com.cloud.gateway.application.policy.PolicyRollbackLock;
import com.cloud.gateway.application.policy.PolicyRollbackState;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.application.policy.PolicyDefinitionAccess;
import com.cloud.gateway.application.policy.PolicyDefinitionReader;
import com.cloud.gateway.application.policy.PolicySnapshotAssembler;
import com.cloud.gateway.application.policy.PolicySnapshotPublication;
import com.cloud.gateway.application.policy.PolicyPublicationCommandService;
import com.cloud.gateway.application.policy.PolicyRollbackCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.application.policy.PolicySnapshotHistory;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.policy.PolicySnapshotPublisher;
import com.cloud.gateway.application.policy.EndpointAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.EndpointAccessPolicyCommandService;
import com.cloud.gateway.application.policy.WritableClientPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.WritableAppPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.WritableEndpointRegistryDefinitionRepository;
import com.cloud.gateway.application.policy.WritableEndpointAccessPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicySnapshotHasher;
import com.cloud.gateway.infrastructure.policy.ClientPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.ClientPropertiesMapStructMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.PolicyCompositionPropertiesMapStructMapper;
import com.cloud.gateway.infrastructure.policy.PolicyCompositionPropertiesMapper;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesClientPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesAppPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesEndpointRegistryDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesPolicySnapshotProvider;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesEndpointAccessPolicyDefinitionRepository;
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
    PolicyCompositionPropertiesMapper policyCompositionPropertiesMapper() {
        return Mappers.getMapper(PolicyCompositionPropertiesMapStructMapper.class);
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
    PolicySnapshotHasher policySnapshotHasher(
            CanonicalPolicyContentMapper mapper,
            ObjectMapper objectMapper
    ) {
        return new CanonicalPolicySnapshotHasher(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    StoredPolicySnapshotMapper storedPolicySnapshotMapper() {
        return new StoredPolicySnapshotMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    StoredPolicySnapshotCodec storedPolicySnapshotCodec(
            StoredPolicySnapshotMapper mapper,
            PolicySnapshotHasher hasher,
            ObjectMapper objectMapper,
            HeaderRules headerRules
    ) {
        return new StoredPolicySnapshotCodec(mapper, hasher, objectMapper, headerRules);
    }

    @Bean
    @ConditionalOnMissingBean
    StoredPolicyDefinitionCodec storedPolicyDefinitionCodec(
            StoredPolicySnapshotMapper mapper,
            ObjectMapper objectMapper,
            HeaderRules headerRules
    ) {
        return new StoredPolicyDefinitionCodec(mapper, objectMapper, headerRules);
    }

    @Bean
    @ConditionalOnMissingBean
    EndpointRegistryDefinitionRepository endpointRegistryDefinitionRepository(
            GatewayPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec
    ) {
        return new PropertiesEndpointRegistryDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    AppPolicyDefinitionRepository appPolicyDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec) {
        return new PropertiesAppPolicyDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    ClientPolicyDefinitionRepository clientPolicyDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredPolicySnapshotMapper mapper,
            ClientPropertiesMapper clients, StoredPolicyDefinitionCodec codec) {
        return new PropertiesClientPolicyDefinitionRepository(properties, mapper, clients, codec);
    }

    @Bean @ConditionalOnMissingBean
    EndpointAccessPolicyDefinitionRepository endpointAccessPolicyDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec) {
        return new PropertiesEndpointAccessPolicyDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    PolicyCompositionDefinitionRepository policyConfigurationDefinitionRepository(
            GatewayPolicyDefinitionProperties properties, StoredPolicySnapshotMapper mapper,
            PolicyCompositionPropertiesMapper policyConfiguration, StoredPolicyDefinitionCodec codec) {
        return new PropertiesPolicyCompositionDefinitionRepository(properties, mapper, policyConfiguration, codec);
    }

    @Bean @ConditionalOnMissingBean
    @ConditionalOnBean({WritablePolicyCompositionDefinitionRepository.class, PolicyRollbackState.class, PolicyPublishLock.class})
    PolicyCompositionCommandService policyConfigurationCommandService(
            WritablePolicyCompositionDefinitionRepository repository, PolicyRollbackState rollbackState, PolicyPublishLock publishLock,
            HeaderRules headerRules) {
        return new PolicyCompositionCommandService(repository, rollbackState, publishLock, headerRules);
    }

    @Bean
    @ConditionalOnMissingBean
    PolicySnapshotProvider policySnapshotProvider(
            EndpointRegistryDefinitionRepository endpointRegistry,
            AppPolicyDefinitionRepository apps,
            ClientPolicyDefinitionRepository clientAccess,
            EndpointAccessPolicyDefinitionRepository endpointAccess,
            PolicyCompositionDefinitionRepository policies,
            PolicySnapshotAssembler assembler,
            Clock clock
    ) {
        return new PropertiesPolicySnapshotProvider(new PolicyDefinitionReader(endpointRegistry, apps, clientAccess,
                endpointAccess, policies), assembler, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    PolicySnapshotAssembler policySnapshotAssembler(PolicySnapshotHasher hasher, HeaderRules headerRules) {
        return new PolicySnapshotAssembler(hasher, headerRules);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LockExecutor.class)
    PolicyPublishLock policyPublishLock(LockExecutor lockExecutor) {
        return new PolicyPublishLock(lockExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LockExecutor.class)
    PolicyRollbackLock policyRollbackLock(LockExecutor lockExecutor) {
        return new PolicyRollbackLock(lockExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableAppPolicyDefinitionRepository.class,
            PolicyRollbackState.class, PolicyPublishLock.class})
    AppPolicyCommandService appPolicyCommandService(
            WritableAppPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState,
            PolicyPublishLock publishLock
    ) {
        return new AppPolicyCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableClientPolicyDefinitionRepository.class,
            PolicyRollbackState.class, PolicyPublishLock.class})
    ClientAccessPolicyCommandService clientAccessPolicyCommandService(
            WritableClientPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState,
            PolicyPublishLock publishLock
    ) {
        return new ClientAccessPolicyCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableClientPolicyDefinitionRepository.class,
            PolicyRollbackState.class, PolicyPublishLock.class})
    ClientCredentialPolicyCommandService clientCredentialPolicyCommandService(
            WritableClientPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState,
            PolicyPublishLock publishLock
    ) {
        return new ClientCredentialPolicyCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableEndpointAccessPolicyDefinitionRepository.class,
            PolicyRollbackState.class, PolicyPublishLock.class})
    EndpointAccessPolicyCommandService endpointAccessPolicyCommandService(
            WritableEndpointAccessPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState,
            PolicyPublishLock publishLock
    ) {
        return new EndpointAccessPolicyCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            WritableEndpointRegistryDefinitionRepository.class,
            WritableAppPolicyDefinitionRepository.class,
            WritableClientPolicyDefinitionRepository.class,
            WritableEndpointAccessPolicyDefinitionRepository.class,
            WritablePolicyCompositionDefinitionRepository.class,
            PolicySnapshotPublisher.class,
            PolicySnapshotHistory.class,
            PolicyRollbackState.class,
            PolicyPublishLock.class,
            PolicyRollbackLock.class
    })
    PolicySnapshotCommandService policySnapshotCommandService(
            WritableEndpointRegistryDefinitionRepository endpointRegistry,
            WritableAppPolicyDefinitionRepository apps,
            WritableClientPolicyDefinitionRepository clientAccess,
            WritableEndpointAccessPolicyDefinitionRepository endpointAccess,
            WritablePolicyCompositionDefinitionRepository policies,
            PolicySnapshotProvider provider,
            PolicySnapshotPublisher publisher,
            PolicySnapshotHistory history,
            PolicyRollbackState rollbackState,
            PolicySnapshotHasher hasher,
            PolicyPublishLock publishLock,
            PolicyRollbackLock rollbackLock,
            Clock clock,
            PolicySnapshotAssembler assembler
    ) {
        PolicyDefinitionAccess definitions = new PolicyDefinitionAccess(
                endpointRegistry, apps, clientAccess, endpointAccess, policies);
        PolicySnapshotPublication publication = new PolicySnapshotPublication(publisher, assembler, clock);
        return new PolicySnapshotCommandService(
                new PolicyPublicationCommandService(definitions, provider, publication, rollbackState,
                        publishLock),
                new PolicyRollbackCommandService(definitions, provider, publication, history, rollbackState,
                        hasher, publishLock, rollbackLock));
    }
}
