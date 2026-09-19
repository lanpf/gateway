package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.common.StringEntityId;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.infrastructure.policy.AccessSnapshotDocumentMapper;
import com.cloud.gateway.infrastructure.policy.CapabilityCompositionStorageMapper;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshot;
import java.util.List;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PolicyStorageMapperConfig.class, uses = CapabilityCompositionStorageMapper.class)
public abstract class AccessSnapshotDocumentMapStructMapper implements AccessSnapshotDocumentMapper {
    @Override
    @Mapping(target = "snapshotVersion", source = "source.version.value")
    public abstract StoredAccessSnapshot snapshot(IngressSnapshot source,
            List<StoredAccessSnapshot.Endpoint> endpoints, Map<String, StoredAccessSnapshot.App> apps,
            Map<String, StoredAccessSnapshot.Client> clients, com.cloud.gateway.domain.policy.bundle.CapabilityComposition policies);
    @Override
    @Mapping(target = "allowedOrigins", source = "access.allowedOrigins")
    public abstract StoredAccessSnapshot.App app(App source);
    @Override
    @Mapping(target = "allowedAppIds", source = "access.allowedApps")
    @Mapping(target = "credentials", source = "access.credentials")
    public abstract StoredAccessSnapshot.Client client(Client source);
    @Override
    public abstract StoredAccessSnapshot.Endpoint endpoint(Endpoint source);
    protected String identifier(StringEntityId source) { return source == null ? null : source.value(); }
    protected String serviceValue(ServiceId source) { return source == null ? null : source.value(); }
    protected String routeValue(RouteId source) { return source == null ? null : source.value(); }
}
