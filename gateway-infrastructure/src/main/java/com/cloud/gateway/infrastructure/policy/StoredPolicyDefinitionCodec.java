package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.policy.header.HeaderRules;

import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.policy.bundle.PolicyComposition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

public final class StoredPolicyDefinitionCodec extends PolicyYamlCodec {
    private static final TypeReference<List<StoredPolicySnapshot.Endpoint>> ENDPOINT_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredPolicySnapshot.App>> APP_TYPE =
            new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredPolicySnapshot.Client>> CLIENT_TYPE =
            new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredPolicySnapshot.Route>> ENDPOINT_ACCESS_TYPE =
            new TypeReference<>() { };

    private final StoredPolicySnapshotMapper mapper;
    private final HeaderRules headerRules;

    public StoredPolicyDefinitionCodec(StoredPolicySnapshotMapper mapper, ObjectMapper objectMapper,
            HeaderRules headerRules) {
        super(objectMapper);
        this.mapper = mapper;
        this.headerRules = headerRules;
    }

    public PolicyComposition decodePolicyConfiguration(String content) {
        return decode(content, StoredPolicyComposition.class, stored -> {
            PolicyComposition decoded = mapper.toDomainPolicies(stored);
            headerRules.validate(decoded);
            return decoded;
        });
    }

    public String encodePolicyConfiguration(PolicyComposition definition) {
        headerRules.validate(definition);
        return encode(mapper.toStoredPolicies(definition));
    }

    public EndpointRegistry decodeEndpointRegistry(String content) {
        return decode(content, ENDPOINT_TYPE, mapper::toDomainEndpointRegistry);
    }

    public Map<AppId, AppPolicy> decodeApps(String content) {
        return decode(content, APP_TYPE, mapper::toDomainApps);
    }

    public Map<ClientId, ClientPolicy> decodeClients(String content) {
        return decode(content, CLIENT_TYPE, mapper::toDomainClients);
    }

    public Map<EndpointId, EndpointPolicy> decodeEndpointAccess(String content) {
        return decode(content, ENDPOINT_ACCESS_TYPE, mapper::toDomainEndpointAccess);
    }

    public String encodeEndpointRegistry(EndpointRegistry definition) {
        return encode(mapper.toStoredEndpoints(definition));
    }

    public String encodeApps(Map<AppId, AppPolicy> definition) {
        return encode(mapper.toStoredApps(definition));
    }

    public String encodeClients(Map<ClientId, ClientPolicy> definition) {
        return encode(mapper.toStoredClients(definition));
    }

    public String encodeEndpointAccess(Map<EndpointId, EndpointPolicy> definition) {
        return encode(mapper.toStoredEndpointAccess(definition));
    }

}
