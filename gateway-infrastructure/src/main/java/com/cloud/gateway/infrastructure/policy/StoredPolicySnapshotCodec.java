package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.header.HeaderRules;

import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StoredPolicySnapshotCodec extends PolicyYamlCodec {
    private final StoredPolicySnapshotMapper mapper;
    private final PolicySnapshotHasher hasher;
    private final HeaderRules headerRules;

    public StoredPolicySnapshotCodec(
            StoredPolicySnapshotMapper mapper,
            PolicySnapshotHasher hasher,
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
        IngressAccessSnapshot snapshot = decode(content, StoredPolicySnapshot.class, stored -> {
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
