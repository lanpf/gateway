package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.application.configuration.publication.AccessSnapshotHasher;
import com.cloud.gateway.domain.capability.configuration.CapabilityComposition;
import com.cloud.gateway.domain.capability.header.rules.HeaderAdmissionRules;

import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class StoredAccessSnapshotCodec extends PolicyYamlCodec {
    private final StoredAccessSnapshotMapper mapper;
    private final AccessSnapshotHasher hasher;
    private final HeaderAdmissionRules headerRules;

    public StoredAccessSnapshotCodec(
            StoredAccessSnapshotMapper mapper,
            AccessSnapshotHasher hasher,
            ObjectMapper objectMapper,
            HeaderAdmissionRules headerRules
    ) {
        super(objectMapper);
        this.mapper = mapper;
        this.hasher = hasher;
        this.headerRules = headerRules;
    }

    public String encodeSnapshot(IngressSnapshot snapshot) {
        validateHeaderAdmission(snapshot.content().capabilities());
        return encode(mapper.toStore(snapshot));
    }

    public IngressSnapshot decodeSnapshot(String content) {
        IngressSnapshot snapshot = decode(content, StoredAccessSnapshot.class, stored -> {
            IngressSnapshot decoded = mapper.toDomain(stored);
            validateHeaderAdmission(decoded.content().capabilities());
            return decoded;
        });
        if (!hasher.hash(snapshot.content()).equals(snapshot.contentHash())) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
        }
        return snapshot;
    }

    private void validateHeaderAdmission(CapabilityComposition composition) {
        composition.bundles().values().forEach(bundle -> headerRules.validate(bundle.find(com.cloud.gateway.domain.capability.header.HeaderConfigDefinition.INSTANCE).orElse(null)));
    }
}
