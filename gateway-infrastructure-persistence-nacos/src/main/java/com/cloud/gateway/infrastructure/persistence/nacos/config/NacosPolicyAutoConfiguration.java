package com.cloud.gateway.infrastructure.persistence.nacos.config;

import com.alibaba.cloud.nacos.NacosConfigAutoConfiguration;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotHistory;
import com.cloud.gateway.application.configuration.definition.WritableClientAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableAppAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableEndpointAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableCapabilityPolicyConfigDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodAccessSnapshotStore;
import com.cloud.gateway.infrastructure.policy.lkg.LastKnownGoodAccessSnapshotStore;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosClientAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosAppAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosEndpointAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosAccessSnapshotStore;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosCapabilityPolicyConfigDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicyHistorySource;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicyHistorySourceAdapter;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosAccessSnapshotHistory;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.DefaultNacosConfigOperations;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.net.http.HttpClient;
import com.cloud.gateway.infrastructure.policy.config.AccessOperationProperties;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.framework.core.error.FrameworkError;
import com.alibaba.nacos.api.PropertyKeyConst;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.client.RestClient;

@AutoConfiguration(beforeName = "com.cloud.gateway.infrastructure.policy.config.PolicyInfrastructureAutoConfiguration")
@AutoConfigureAfter({NacosConfigAutoConfiguration.class, RestClientAutoConfiguration.class})
@EnableConfigurationProperties({NacosPolicyProperties.class, NacosPolicyHistoryProperties.class})
public class NacosPolicyAutoConfiguration {
    private static final String DEFAULT_CONTEXT_PATH = "/nacos";

    @Bean
    NacosConfigOperations nacosConfigOperations(NacosConfigManager manager,
            NacosPolicyProperties properties, AccessOperationProperties operationProperties) {
        validateBlockingLimits(manager.getNacosConfigProperties(), properties, operationProperties);
        return new DefaultNacosConfigOperations(manager.getConfigService());
    }

    @Bean
    LastKnownGoodAccessSnapshotStore lastKnownGoodAccessSnapshotStore(NacosPolicyProperties properties) {
        return new FileLastKnownGoodAccessSnapshotStore(properties.getLastKnownGoodLocation());
    }

    @Bean(initMethod = "initialize", destroyMethod = "close")
    NacosAccessSnapshotStore nacosAccessSnapshotStore(
            NacosConfigOperations operations,
            NacosPolicyProperties properties,
            StoredAccessSnapshotCodec codec,
            LastKnownGoodAccessSnapshotStore lastKnownGood,
            ObjectProvider<MeterRegistry> meterRegistries
    ) {
        NacosAccessSnapshotStore store = new NacosAccessSnapshotStore(operations, properties, codec, lastKnownGood);
        meterRegistries.ifAvailable(store::bindMetrics);
        return store;
    }

    @Bean
    com.cloud.gateway.application.configuration.definition.WritableEndpointAccessDefinitionRepository nacosEndpointAccessDefinitionRepository(
            NacosConfigOperations operations,
            NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec
    ) {
        return new NacosEndpointAccessDefinitionRepository(operations, properties, codec);
    }

    @Bean
    com.cloud.gateway.application.configuration.definition.WritableAppAccessDefinitionRepository nacosAppAccessDefinitionRepository(
            NacosConfigOperations operations, NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        return new NacosAppAccessDefinitionRepository(operations, properties, codec);
    }

    @Bean
    com.cloud.gateway.application.configuration.definition.WritableClientAccessDefinitionRepository nacosClientAccessDefinitionRepository(
            NacosConfigOperations operations, NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        return new NacosClientAccessDefinitionRepository(operations, properties, codec);
    }

    @Bean
    com.cloud.gateway.application.configuration.definition.WritableCapabilityPolicyConfigDefinitionRepository nacosCapabilityPolicyConfigDefinitionRepository(
            NacosConfigOperations operations, NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        return new NacosCapabilityPolicyConfigDefinitionRepository(operations, properties, codec);
    }

    @Bean
    @ConditionalOnMissingBean
    RestClient.Builder nacosPolicyRestClientBuilder() {
        // 反应式网关没有 servlet 的 RestClientAutoConfiguration，History REST 客户端
        // 需要自备 Builder；servlet 部署已有该 bean 时让位于既有实现。
        return RestClient.builder();
    }

    @Bean
    NacosPolicyHistorySource nacosPolicyHistorySource(
            NacosPolicyProperties policyProperties,
            NacosPolicyHistoryProperties historyProperties,
            NacosConfigProperties nacosProperties,
            RestClient.Builder restClientBuilder,
            Clock clock
    ) {
        return new NacosPolicyHistorySourceAdapter(
                historyRestClient(restClientBuilder, historyProperties, nacosProperties),
                policyProperties,
                historyProperties,
                nacosProperties,
                clock);
    }

    @Bean
    com.cloud.gateway.application.configuration.publication.AccessSnapshotHistory policySnapshotHistory(
            NacosPolicyHistorySource source,
            StoredAccessSnapshotCodec codec
    ) {
        return new NacosAccessSnapshotHistory(source, codec);
    }

    static void validateBlockingLimits(NacosConfigProperties nacosProperties,
            NacosPolicyProperties properties, AccessOperationProperties operations) {
        long sdkTimeoutMillis = Long.parseLong(nacosProperties.assembleConfigServiceProperties()
                .getProperty(PropertyKeyConst.CONFIG_REQUEST_TIMEOUT, "-1"));
        // Nacos 3.0.3 retries may consume one more full request timeout before returning.
        long blockingMillis = operations.getMaxBlockingTime().toMillis();
        if (sdkTimeoutMillis <= 0 || sdkTimeoutMillis > blockingMillis / 2
                || properties.getReadTimeout().toMillis() > blockingMillis / 2) {
            throw new FrameworkException(FrameworkError.OBJECT_INVALID,
                    "Positive Nacos configRequestTimeout and read timeout must fit half the maximum blocking time");
        }
    }

    private static RestClient historyRestClient(
            RestClient.Builder builder,
            NacosPolicyHistoryProperties properties,
            NacosConfigProperties nacosProperties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getRestClient().getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getRestClient().getReadTimeout());
        return builder.clone()
                .baseUrl(historyBaseUrl(nacosProperties))
                .requestFactory(requestFactory)
                .build();
    }

    private static String historyBaseUrl(NacosConfigProperties properties) {
        String address = properties.getServerAddr().split(",", 2)[0].trim();
        String server = address.startsWith("http://") || address.startsWith("https://")
                ? address
                : "http://" + address;
        String contextPath = StringUtils.hasText(properties.getContextPath())
                ? "/" + properties.getContextPath().replaceAll("^/+|/+$", "")
                : DEFAULT_CONTEXT_PATH;
        return server.replaceAll("/+$", "") + contextPath;
    }
}
