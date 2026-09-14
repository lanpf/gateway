package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.header.HeaderRules;

import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
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
    private static final TypeReference<Map<String, StoredAccessSnapshot.Route>> ENDPOINT_ACCESS_TYPE =
            new TypeReference<>() { };

    private final StoredAccessSnapshotMapper mapper;
    private final HeaderRules headerRules;

    public StoredPolicyDefinitionCodec(StoredAccessSnapshotMapper mapper, ObjectMapper objectMapper,
            HeaderRules headerRules) {
        super(objectMapper);
        this.mapper = mapper;
        this.headerRules = headerRules;
    }

    public PolicyConfig decodePolicyConfiguration(String content) {
        return decode(content, StoredPolicyConfig.class, stored -> {
            PolicyConfig decoded = mapper.toDomainPolicies(stored);
            headerRules.validate(decoded);
            return decoded;
        });
    }

    public String encodePolicyConfiguration(PolicyConfig definition) {
        headerRules.validate(definition);
        return encode(mapper.toStoredPolicies(definition));
    }

    public EndpointRegistry decodeEndpointRegistry(String content) {
        return decode(content, ENDPOINT_TYPE, mapper::toDomainEndpointRegistry);
    }

    public Map<AppId, AppAccess> decodeApps(String content) {
        return decode(content, APP_TYPE, mapper::toDomainApps);
    }

    public Map<ClientId, ClientAccess> decodeClients(String content) {
        return decode(content, CLIENT_TYPE, mapper::toDomainClients);
    }

    public Map<EndpointId, EndpointAccess> decodeEndpointAccess(String content) {
        return decode(content, ENDPOINT_ACCESS_TYPE, mapper::toDomainEndpointAccess);
    }

    public String encodeEndpointRegistry(EndpointRegistry definition) {
        return encode(mapper.toStoredEndpoints(definition));
    }

    public String encodeApps(Map<AppId, AppAccess> definition) {
        return encode(mapper.toStoredApps(definition));
    }

    public String encodeClients(Map<ClientId, ClientAccess> definition) {
        return encode(mapper.toStoredClients(definition));
    }

    public String encodeEndpointAccess(Map<EndpointId, EndpointAccess> definition) {
        return encode(mapper.toStoredEndpointAccess(definition));
    }

}
