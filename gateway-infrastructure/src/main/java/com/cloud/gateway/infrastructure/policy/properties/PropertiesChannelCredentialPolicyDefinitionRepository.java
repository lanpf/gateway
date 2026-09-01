package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.ChannelCredentialPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.IngressPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesChannelCredentialPolicyDefinitionRepository
        implements ChannelCredentialPolicyDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<ChannelCode, IngressChannelCredentialPolicy>> source;

    public PropertiesChannelCredentialPolicyDefinitionRepository(IngressPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper, StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainChannelCredentials(properties.toStoredChannelCredentials()),
                codec::encodeChannelCredentials);
    }

    @Override public VersionedPolicyDefinition<Map<ChannelCode, IngressChannelCredentialPolicy>> load() {
        return source.load();
    }
}
