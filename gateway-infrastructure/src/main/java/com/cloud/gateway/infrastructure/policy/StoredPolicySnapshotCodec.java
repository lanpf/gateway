package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.header.HeaderRules;

import com.cloud.gateway.application.access.publication.AccessSnapshotHasher;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StoredAccessSnapshotCodec extends PolicyYamlCodec {
    private final StoredAccessSnapshotMapper mapper;
    private final com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher;
    private final HeaderRules headerRules;

    public StoredAccessSnapshotCodec(
            StoredAccessSnapshotMapper mapper,
            com.cloud.gateway.application.access.publication.AccessSnapshotHasher hasher,
            ObjectMapper objectMapper,
            HeaderRules headerRules
    ) {
        super(objectMapper);
        this.mapper = mapper;
        this.hasher = hasher;
        this.headerRules = headerRules;
    }

    public String encodeSnapshot(IngressAccessSnapshot snapshot) {
        headerRules.validate(snapshot.content().policies());
        return encode(mapper.toStore(snapshot));
    }

    public IngressAccessSnapshot decodeSnapshot(String content) {
        IngressAccessSnapshot snapshot = decode(content, StoredAccessSnapshot.class, stored -> {
            IngressAccessSnapshot decoded = mapper.toDomain(stored);
            headerRules.validate(decoded.content().policies());
            return decoded;
        });
        if (!hasher.hash(snapshot.content()).equals(snapshot.contentHash())) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
        }
        return snapshot;
    }
}
