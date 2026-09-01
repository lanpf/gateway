package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

public final class StoredPolicyDefinitionCodec extends PolicyYamlCodec {
    private static final TypeReference<List<StoredPolicySnapshot.Endpoint>> ENDPOINT_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredPolicySnapshot.ClientApp>> CLIENT_APP_TYPE =
            new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredPolicySnapshot.ChannelAccess>> CHANNEL_ACCESS_TYPE =
            new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredPolicySnapshot.ChannelCredential>> CHANNEL_CREDENTIAL_TYPE =
            new TypeReference<>() { };
    private static final TypeReference<Map<String, StoredPolicySnapshot.RouteAccess>> ROUTE_ACCESS_TYPE =
            new TypeReference<>() { };

    private final StoredPolicySnapshotMapper mapper;

    public StoredPolicyDefinitionCodec(StoredPolicySnapshotMapper mapper, ObjectMapper objectMapper) {
        super(objectMapper);
        this.mapper = mapper;
    }

    public EndpointCatalog decodeEndpointCatalog(String content) {
        return decode(content, ENDPOINT_TYPE, mapper::toDomainEndpointCatalog);
    }

    public Map<ClientAppId, IngressClientAppPolicy> decodeClientApps(String content) {
        return decode(content, CLIENT_APP_TYPE, mapper::toDomainClientApps);
    }

    public Map<ChannelCode, IngressChannelAccessPolicy> decodeChannelAccess(String content) {
        return decode(content, CHANNEL_ACCESS_TYPE, mapper::toDomainChannelAccess);
    }

    public Map<ChannelCode, IngressChannelCredentialPolicy> decodeChannelCredentials(String content) {
        return decode(content, CHANNEL_CREDENTIAL_TYPE, mapper::toDomainChannelCredentials);
    }

    public Map<EndpointId, IngressRouteAccessPolicy> decodeRouteAccess(String content) {
        return decode(content, ROUTE_ACCESS_TYPE, mapper::toDomainRouteAccess);
    }

    public String encodeEndpointCatalog(EndpointCatalog definition) {
        return encode(mapper.toStoredEndpoints(definition));
    }

    public String encodeClientApps(Map<ClientAppId, IngressClientAppPolicy> definition) {
        return encode(mapper.toStoredClientApps(definition));
    }

    public String encodeChannelAccess(Map<ChannelCode, IngressChannelAccessPolicy> definition) {
        return encode(mapper.toStoredChannelAccess(definition));
    }

    public String encodeChannelCredentials(Map<ChannelCode, IngressChannelCredentialPolicy> definition) {
        return encode(mapper.toStoredChannelCredentials(definition));
    }

    public String encodeRouteAccess(Map<EndpointId, IngressRouteAccessPolicy> definition) {
        return encode(mapper.toStoredRouteAccess(definition));
    }

}
