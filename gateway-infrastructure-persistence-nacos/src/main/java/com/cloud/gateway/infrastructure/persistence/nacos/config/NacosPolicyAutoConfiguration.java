package com.cloud.gateway.infrastructure.persistence.nacos.config;

import com.alibaba.cloud.nacos.NacosConfigAutoConfiguration;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.cloud.gateway.application.policy.PolicySnapshotHistory;
import com.cloud.gateway.application.policy.WritableChannelAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.WritableChannelCredentialPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.WritableClientAppPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.WritableEndpointCatalogDefinitionRepository;
import com.cloud.gateway.application.policy.WritableRouteAccessPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.policy.lkg.FileLastKnownGoodPolicySnapshotStore;
import com.cloud.gateway.infrastructure.policy.lkg.LastKnownGoodPolicySnapshotStore;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosChannelAccessPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosChannelCredentialPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosClientAppPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosEndpointCatalogDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosPolicySnapshotStore;
import com.cloud.gateway.infrastructure.persistence.nacos.NacosRouteAccessPolicyDefinitionRepository;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicyHistorySource;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicyHistorySourceAdapter;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicySnapshotHistory;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.DefaultNacosConfigOperations;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.net.http.HttpClient;
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
    NacosConfigOperations nacosConfigOperations(NacosConfigManager manager) {
        return new DefaultNacosConfigOperations(manager.getConfigService());
    }

    @Bean
    LastKnownGoodPolicySnapshotStore lastKnownGoodPolicySnapshotStore(NacosPolicyProperties properties) {
        return new FileLastKnownGoodPolicySnapshotStore(properties.getLastKnownGoodLocation());
    }

    @Bean(initMethod = "initialize", destroyMethod = "close")
    NacosPolicySnapshotStore nacosPolicySnapshotStore(
            NacosConfigOperations operations,
            NacosPolicyProperties properties,
            StoredPolicySnapshotCodec codec,
            LastKnownGoodPolicySnapshotStore lastKnownGood
    ) {
        return new NacosPolicySnapshotStore(operations, properties, codec, lastKnownGood);
    }

    @Bean
    WritableEndpointCatalogDefinitionRepository nacosEndpointCatalogDefinitionRepository(
            NacosConfigOperations operations,
            NacosPolicyProperties properties,
            StoredPolicyDefinitionCodec codec
    ) {
        return new NacosEndpointCatalogDefinitionRepository(operations, properties, codec);
    }

    @Bean
    WritableClientAppPolicyDefinitionRepository nacosClientAppPolicyDefinitionRepository(
            NacosConfigOperations operations, NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        return new NacosClientAppPolicyDefinitionRepository(operations, properties, codec);
    }

    @Bean
    WritableChannelAccessPolicyDefinitionRepository nacosChannelAccessPolicyDefinitionRepository(
            NacosConfigOperations operations, NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        return new NacosChannelAccessPolicyDefinitionRepository(operations, properties, codec);
    }

    @Bean
    WritableChannelCredentialPolicyDefinitionRepository nacosChannelCredentialPolicyDefinitionRepository(
            NacosConfigOperations operations, NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        return new NacosChannelCredentialPolicyDefinitionRepository(operations, properties, codec);
    }

    @Bean
    WritableRouteAccessPolicyDefinitionRepository nacosRouteAccessPolicyDefinitionRepository(
            NacosConfigOperations operations, NacosPolicyProperties properties, StoredPolicyDefinitionCodec codec) {
        return new NacosRouteAccessPolicyDefinitionRepository(operations, properties, codec);
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
    PolicySnapshotHistory policySnapshotHistory(
            NacosPolicyHistorySource source,
            StoredPolicySnapshotCodec codec
    ) {
        return new NacosPolicySnapshotHistory(source, codec);
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
