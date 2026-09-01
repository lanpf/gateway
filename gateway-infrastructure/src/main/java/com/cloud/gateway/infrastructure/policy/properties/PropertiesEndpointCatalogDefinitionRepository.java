package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.EndpointCatalogDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.IngressPolicyDefinitionProperties;

public final class PropertiesEndpointCatalogDefinitionRepository implements EndpointCatalogDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<EndpointCatalog> source;

    public PropertiesEndpointCatalogDefinitionRepository(
            IngressPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper,
            StoredPolicyDefinitionCodec codec
    ) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainEndpointCatalog(properties.toStoredEndpoints()),
                codec::encodeEndpointCatalog);
    }

    @Override public VersionedPolicyDefinition<EndpointCatalog> load() { return source.load(); }
}
