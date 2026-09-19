package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.application.configuration.publication.AccessSnapshotHasher;
import com.cloud.gateway.domain.policy.header.HeaderRules;

import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StoredAccessSnapshotCodec extends PolicyYamlCodec {
    private final StoredAccessSnapshotMapper mapper;
    private final AccessSnapshotHasher hasher;
    private final HeaderRules headerRules;

    public StoredAccessSnapshotCodec(
            StoredAccessSnapshotMapper mapper,
            AccessSnapshotHasher hasher,
            ObjectMapper objectMapper,
            HeaderRules headerRules
    ) {
        super(objectMapper);
        this.mapper = mapper;
        this.hasher = hasher;
        this.headerRules = headerRules;
    }

    public String encodeSnapshot(IngressSnapshot snapshot) {
        headerRules.validate(snapshot.content().capabilityComposition());
        return encode(mapper.toStore(snapshot));
    }

    public IngressSnapshot decodeSnapshot(String content) {
        IngressSnapshot snapshot = decode(content, StoredAccessSnapshot.class, stored -> {
            IngressSnapshot decoded = mapper.toDomain(stored);
            headerRules.validate(decoded.content().capabilityComposition());
            return decoded;
        });
        if (!hasher.hash(snapshot.content()).equals(snapshot.contentHash())) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
        }
        return snapshot;
    }
}
