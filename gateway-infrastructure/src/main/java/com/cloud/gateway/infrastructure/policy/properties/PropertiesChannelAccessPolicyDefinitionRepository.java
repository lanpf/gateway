package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.ChannelAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.IngressPolicyDefinitionProperties;
import java.util.Map;

public final class PropertiesChannelAccessPolicyDefinitionRepository
        implements ChannelAccessPolicyDefinitionRepository {
    private final PropertiesPolicyDefinitionSource<Map<ChannelCode, IngressChannelAccessPolicy>> source;

    public PropertiesChannelAccessPolicyDefinitionRepository(IngressPolicyDefinitionProperties properties,
            StoredPolicySnapshotMapper mapper, StoredPolicyDefinitionCodec codec) {
        source = new PropertiesPolicyDefinitionSource<>(
                () -> mapper.toDomainChannelAccess(properties.toStoredChannelAccess()),
                codec::encodeChannelAccess);
    }

    @Override public VersionedPolicyDefinition<Map<ChannelCode, IngressChannelAccessPolicy>> load() {
        return source.load();
    }
}
