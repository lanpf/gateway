package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CanonicalPolicySnapshotHasher implements PolicySnapshotHasher {
    private static final String HASH_ALGORITHM = "SHA-256";

    private final CanonicalPolicyContentMapper mapper;
    private final ObjectMapper objectMapper;

    public CanonicalPolicySnapshotHasher(
            CanonicalPolicyContentMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper.copy()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override
    public String hash(IngressPolicyContent content) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(mapper.toCanonical(content));
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new InfrastructureException(InfrastructureError.INFRA_TECH_POLICY_SERIALIZATION_FAILED, exception);
        }
    }
}
