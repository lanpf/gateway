package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StoredPolicySnapshotCodec extends PolicyYamlCodec {
    private final StoredPolicySnapshotMapper mapper;
    private final PolicySnapshotHasher hasher;

    public StoredPolicySnapshotCodec(
            StoredPolicySnapshotMapper mapper,
            PolicySnapshotHasher hasher,
            ObjectMapper objectMapper
    ) {
        super(objectMapper);
        this.mapper = mapper;
        this.hasher = hasher;
    }

    public String encodeSnapshot(IngressPolicySnapshot snapshot) {
        return encode(mapper.toStore(snapshot));
    }

    public IngressPolicySnapshot decodeSnapshot(String content) {
        IngressPolicySnapshot snapshot = decode(content, StoredPolicySnapshot.class, mapper::toDomain);
        if (!hasher.hash(snapshot.content()).equals(snapshot.contentHash())) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
        }
        return snapshot;
    }
}
