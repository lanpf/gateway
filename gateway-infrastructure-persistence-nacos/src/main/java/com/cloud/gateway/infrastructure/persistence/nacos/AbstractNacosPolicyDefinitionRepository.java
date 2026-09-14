package com.cloud.gateway.infrastructure.persistence.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.cloud.gateway.application.access.definition.VersionedAccessDefinition;
import com.cloud.gateway.application.access.definition.WritableAccessDefinitionRepository;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.function.Function;
import org.springframework.util.StringUtils;

abstract class AbstractNacosAccessDefinitionRepository<T> implements com.cloud.gateway.application.access.definition.WritableAccessDefinitionRepository<T> {
    private final NacosConfigOperations operations;
    private final NacosPolicyProperties properties;
    private final String dataId;
    private final Function<String, T> decoder;
    private final Function<T, String> encoder;

    AbstractNacosAccessDefinitionRepository(NacosConfigOperations operations, NacosPolicyProperties properties,
            String dataId, Function<String, T> decoder, Function<T, String> encoder) {
        this.operations = operations;
        this.properties = properties;
        this.dataId = dataId;
        this.decoder = decoder;
        this.encoder = encoder;
    }

    @Override
    public final com.cloud.gateway.application.access.definition.VersionedAccessDefinition<T> load() {
        try {
            String content = operations.getConfig(dataId, properties.getGroup(), properties.getReadTimeout().toMillis());
            if (!StringUtils.hasText(content)) {
                throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
            }
            try {
                return new com.cloud.gateway.application.access.definition.VersionedAccessDefinition<>(decoder.apply(content),
                        NacosAccessSnapshotStore.sourceRevision(content));
            } catch (RuntimeException exception) {
                throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID, exception);
            }
        } catch (NacosException exception) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
        }
    }

    @Override
    public final boolean compareAndSet(String expectedSourceRevision, T definition) {
        try {
            return operations.publishConfigCas(dataId, properties.getGroup(), encoder.apply(definition),
                    expectedSourceRevision);
        } catch (NacosException exception) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
        }
    }
}
