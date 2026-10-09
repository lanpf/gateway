package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator;

import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

public final class StoredPolicyDefinitionCodec extends AbstractPolicyYamlCodec {
    private static final TypeReference<List<StoredAccessSnapshot.Endpoint>> ENDPOINT_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredAccessSnapshot.App>> APP_TYPE =
            new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredAccessSnapshot.Client>> CLIENT_TYPE =
            new TypeReference<>() { };

    private final StoredAccessSnapshotMapper mapper;
    private final CapabilityPolicyConfigValidator validator;

    public StoredPolicyDefinitionCodec(StoredAccessSnapshotMapper mapper, ObjectMapper objectMapper,
            CapabilityPolicyConfigValidator validator) {
        super(objectMapper);
        this.mapper = mapper;
        this.validator = validator;
    }

    public CapabilityPolicyConfig decodeCapabilityPolicyConfig(String content) {
        return decode(content, StoredCapabilityPolicyConfig.class, stored -> {
            CapabilityPolicyConfig decoded = mapper.toDomainPolicies(stored);
            validator.validate(decoded);
            return decoded;
        });
    }

    public String encodeCapabilityPolicyConfig(CapabilityPolicyConfig definition) {
        validator.validate(definition);
        return encode(mapper.toStoredPolicies(definition));
    }


    public EndpointRegistry decodeEndpointRegistry(String content) {
        return decode(content, ENDPOINT_TYPE, mapper::toDomainEndpointRegistry);
    }

    public AppRegistry decodeApps(String content) {
        return decode(content, APP_TYPE, mapper::toDomainApps);
    }

    public ClientRegistry decodeClients(String content) {
        return decode(content, CLIENT_TYPE, mapper::toDomainClients);
    }

    public String encodeEndpointRegistry(EndpointRegistry definition) {
        return encode(mapper.toStoredEndpoints(definition));
    }

    public String encodeApps(AppRegistry definition) {
        return encode(mapper.toStoredApps(definition));
    }

    public String encodeClients(ClientRegistry definition) {
        return encode(mapper.toStoredClients(definition));
    }

}
