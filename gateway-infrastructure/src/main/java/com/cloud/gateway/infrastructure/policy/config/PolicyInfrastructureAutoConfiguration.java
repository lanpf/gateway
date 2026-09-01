package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.application.policy.ChannelAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.ChannelAccessPolicyCommandService;
import com.cloud.gateway.application.policy.ChannelCredentialPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.ChannelCredentialPolicyCommandService;
import com.cloud.gateway.application.policy.ClientAppPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.ClientAppPolicyCommandService;
import com.cloud.gateway.application.policy.EndpointCatalogDefinitionRepository;
import com.cloud.gateway.application.policy.PolicyPublishLock;
import com.cloud.gateway.application.policy.PolicyRollbackLock;
import com.cloud.gateway.application.policy.PolicyRollbackState;
import com.cloud.gateway.application.policy.PolicySnapshotCommandService;
import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.application.policy.PolicySnapshotHistory;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.policy.PolicySnapshotPublisher;
import com.cloud.gateway.application.policy.RouteAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.RouteAccessPolicyCommandService;
import com.cloud.gateway.application.policy.WritableChannelAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.WritableChannelCredentialPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.WritableClientAppPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.WritableEndpointCatalogDefinitionRepository;
import com.cloud.gateway.application.policy.WritableRouteAccessPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicyContentMapper;
import com.cloud.gateway.infrastructure.policy.CanonicalPolicySnapshotHasher;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesChannelAccessPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesChannelCredentialPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesClientAppPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesEndpointCatalogDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesPolicySnapshotProvider;
import com.cloud.gateway.infrastructure.policy.properties.PropertiesRouteAccessPolicyDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(name = {
        "com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyAutoConfiguration",
        "com.cloud.gateway.infrastructure.coordination.redis.config.RedisPolicyCoordinationAutoConfiguration"
})
@EnableConfigurationProperties(IngressPolicyDefinitionProperties.class)
public class PolicyInfrastructureAutoConfiguration {
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
            ObjectMapper objectMapper
    ) {
        return new StoredPolicySnapshotCodec(mapper, hasher, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    StoredPolicyDefinitionCodec storedPolicyDefinitionCodec(
            StoredPolicySnapshotMapper mapper,
            ObjectMapper objectMapper
    ) {
        return new StoredPolicyDefinitionCodec(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    EndpointCatalogDefinitionRepository endpointCatalogDefinitionRepository(
            IngressPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec
    ) {
        return new PropertiesEndpointCatalogDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    ClientAppPolicyDefinitionRepository clientAppPolicyDefinitionRepository(
            IngressPolicyDefinitionProperties properties, StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec) {
        return new PropertiesClientAppPolicyDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    ChannelAccessPolicyDefinitionRepository channelAccessPolicyDefinitionRepository(
            IngressPolicyDefinitionProperties properties, StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec) {
        return new PropertiesChannelAccessPolicyDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    ChannelCredentialPolicyDefinitionRepository channelCredentialPolicyDefinitionRepository(
            IngressPolicyDefinitionProperties properties, StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec) {
        return new PropertiesChannelCredentialPolicyDefinitionRepository(properties, mapper, codec);
    }

    @Bean @ConditionalOnMissingBean
    RouteAccessPolicyDefinitionRepository routeAccessPolicyDefinitionRepository(
            IngressPolicyDefinitionProperties properties, StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec) {
        return new PropertiesRouteAccessPolicyDefinitionRepository(properties, mapper, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    PolicySnapshotProvider policySnapshotProvider(
            EndpointCatalogDefinitionRepository endpointCatalog,
            ClientAppPolicyDefinitionRepository clientApps,
            ChannelAccessPolicyDefinitionRepository channelAccess,
            ChannelCredentialPolicyDefinitionRepository channelCredentials,
            RouteAccessPolicyDefinitionRepository routeAccess,
            PolicySnapshotHasher hasher,
            Clock clock
    ) {
        return new PropertiesPolicySnapshotProvider(endpointCatalog, clientApps, channelAccess,
                channelCredentials, routeAccess, hasher, clock);
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
    @ConditionalOnBean({WritableClientAppPolicyDefinitionRepository.class,
            PolicyRollbackState.class, PolicyPublishLock.class})
    ClientAppPolicyCommandService clientAppPolicyCommandService(
            WritableClientAppPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState,
            PolicyPublishLock publishLock
    ) {
        return new ClientAppPolicyCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableChannelAccessPolicyDefinitionRepository.class,
            PolicyRollbackState.class, PolicyPublishLock.class})
    ChannelAccessPolicyCommandService channelAccessPolicyCommandService(
            WritableChannelAccessPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState,
            PolicyPublishLock publishLock
    ) {
        return new ChannelAccessPolicyCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableChannelCredentialPolicyDefinitionRepository.class,
            PolicyRollbackState.class, PolicyPublishLock.class})
    ChannelCredentialPolicyCommandService channelCredentialPolicyCommandService(
            WritableChannelCredentialPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState,
            PolicyPublishLock publishLock
    ) {
        return new ChannelCredentialPolicyCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({WritableRouteAccessPolicyDefinitionRepository.class,
            PolicyRollbackState.class, PolicyPublishLock.class})
    RouteAccessPolicyCommandService routeAccessPolicyCommandService(
            WritableRouteAccessPolicyDefinitionRepository repository,
            PolicyRollbackState rollbackState,
            PolicyPublishLock publishLock
    ) {
        return new RouteAccessPolicyCommandService(repository, rollbackState, publishLock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            WritableEndpointCatalogDefinitionRepository.class,
            WritableClientAppPolicyDefinitionRepository.class,
            WritableChannelAccessPolicyDefinitionRepository.class,
            WritableChannelCredentialPolicyDefinitionRepository.class,
            WritableRouteAccessPolicyDefinitionRepository.class,
            PolicySnapshotPublisher.class,
            PolicySnapshotHistory.class,
            PolicyRollbackState.class,
            PolicyPublishLock.class,
            PolicyRollbackLock.class
    })
    PolicySnapshotCommandService policySnapshotCommandService(
            WritableEndpointCatalogDefinitionRepository endpointCatalog,
            WritableClientAppPolicyDefinitionRepository clientApps,
            WritableChannelAccessPolicyDefinitionRepository channelAccess,
            WritableChannelCredentialPolicyDefinitionRepository channelCredentials,
            WritableRouteAccessPolicyDefinitionRepository routeAccess,
            PolicySnapshotProvider provider,
            PolicySnapshotPublisher publisher,
            PolicySnapshotHistory history,
            PolicyRollbackState rollbackState,
            PolicySnapshotHasher hasher,
            PolicyPublishLock publishLock,
            PolicyRollbackLock rollbackLock,
            Clock clock
    ) {
        return new PolicySnapshotCommandService(
                endpointCatalog, clientApps, channelAccess, channelCredentials, routeAccess,
                provider, publisher, history, rollbackState, hasher, publishLock, rollbackLock, clock);
    }
}
