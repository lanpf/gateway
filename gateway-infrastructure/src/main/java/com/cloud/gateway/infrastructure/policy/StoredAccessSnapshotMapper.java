package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.app.AppRegistry;
import com.cloud.gateway.domain.client.ClientRegistry;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;

import java.util.List;
import java.util.Map;

public interface StoredAccessSnapshotMapper {
    StoredCapabilityPolicyConfig toStoredPolicies(CapabilityPolicyConfig source);
    CapabilityPolicyConfig toDomainPolicies(StoredCapabilityPolicyConfig source);
    StoredAccessSnapshot toStore(IngressSnapshot source);
    IngressSnapshot toDomain(StoredAccessSnapshot source);
    IngressContent toDomainContent(StoredAccessSnapshot source);
    List<StoredAccessSnapshot.Endpoint> toStoredEndpoints(EndpointRegistry source);
    Map<String, StoredAccessSnapshot.App> toStoredApps(AppRegistry source);
    Map<String, StoredAccessSnapshot.Client> toStoredClients(ClientRegistry source);
    EndpointRegistry toDomainEndpointRegistry(List<StoredAccessSnapshot.Endpoint> source);
    AppRegistry toDomainApps(Map<String, StoredAccessSnapshot.App> source);
    ClientRegistry toDomainClients(Map<String, StoredAccessSnapshot.Client> source);
}
