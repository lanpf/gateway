package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.header.HeaderRules;

import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

public final class StoredPolicyDefinitionCodec extends PolicyYamlCodec {
    private static final TypeReference<List<StoredAccessSnapshot.Endpoint>> ENDPOINT_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredAccessSnapshot.App>> APP_TYPE =
            new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredAccessSnapshot.Client>> CLIENT_TYPE =
            new TypeReference<>() { };

    private final StoredAccessSnapshotMapper mapper;
    private final HeaderRules headerRules;

    public StoredPolicyDefinitionCodec(StoredAccessSnapshotMapper mapper, ObjectMapper objectMapper,
            HeaderRules headerRules) {
        super(objectMapper);
        this.mapper = mapper;
        this.headerRules = headerRules;
    }

    public CapabilityComposition decodeCapabilityComposition(String content) {
        return decode(content, StoredCapabilityComposition.class, stored -> {
            CapabilityComposition decoded = mapper.toDomainPolicies(stored);
            headerRules.validate(decoded);
            return decoded;
        });
    }

    public String encodeCapabilityComposition(CapabilityComposition definition) {
        headerRules.validate(definition);
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
