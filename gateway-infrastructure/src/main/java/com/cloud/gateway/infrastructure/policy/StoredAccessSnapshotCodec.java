package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.application.configuration.publication.AccessSnapshotHasher;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator;

import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StoredAccessSnapshotCodec extends AbstractPolicyYamlCodec {
    private final StoredAccessSnapshotMapper mapper;
    private final AccessSnapshotHasher hasher;
    private final CapabilityPolicyConfigValidator validator;

    public StoredAccessSnapshotCodec(
            StoredAccessSnapshotMapper mapper,
            AccessSnapshotHasher hasher,
            ObjectMapper objectMapper,
            CapabilityPolicyConfigValidator validator
    ) {
        super(objectMapper);
        this.mapper = mapper;
        this.hasher = hasher;
        this.validator = validator;
    }

    public String encodeSnapshot(IngressSnapshot snapshot) {
        validateSnapshot(snapshot);
        return encode(mapper.toStore(snapshot));
    }

    public IngressSnapshot decodeSnapshot(String content) {
        IngressSnapshot snapshot = decode(content, StoredAccessSnapshot.class, stored -> {
            IngressSnapshot decoded = mapper.toDomain(stored);
            validateSnapshot(decoded);
            return decoded;
        });
        if (!hasher.hash(snapshot.content()).equals(snapshot.contentHash())) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
        }
        return snapshot;
    }

    private void validateSnapshot(IngressSnapshot snapshot) {
        validator.validate(snapshot.content().policyConfig(), snapshot.content().apps(),
                snapshot.content().clients(), snapshot.content().endpoints());
    }

}
