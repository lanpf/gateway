package com.cloud.gateway.infrastructure.persistence.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import java.util.function.Function;
import org.springframework.util.StringUtils;

abstract class AbstractNacosAccessDefinitionRepository<T> implements com.cloud.gateway.application.configuration.definition.WritableAccessDefinitionRepository<T> {
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
    public final RevisionedAccessDefinition<T> load() {
        try {
            String content = operations.getConfig(dataId, properties.getGroup(), properties.getReadTimeout().toMillis());
            if (!StringUtils.hasText(content)) {
                throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
            }
            try {
                return new RevisionedAccessDefinition<>(decoder.apply(content),
                        new Revision(NacosAccessSnapshotStore.sourceRevision(content)));
            } catch (RuntimeException exception) {
                // The codec reports a generic deserialization failure; this adapter names the
                // real cause. The domain reason stays visible as the cause of this exception.
                throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID, exception);
            }
        } catch (NacosException exception) {
            throw new InfrastructureException(PersistenceNacosError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
        }
    }

    @Override
    public final Revision compareAndSet(Revision expectedSourceRevision, T definition) {
        String content = encoder.apply(definition);
        try {
            if (!operations.publishConfigCas(dataId, properties.getGroup(), content,
                    expectedSourceRevision.value())) {
                return null;
            }
            return new Revision(NacosAccessSnapshotStore.sourceRevision(content));
        } catch (NacosException exception) {
            throw new InfrastructureException(PersistenceNacosError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
        }
    }
}
