package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import java.util.List;
import java.util.Map;

/** Structural storage projection; domain reconstruction and canonical ordering live outside this mapper. */
public interface AccessSnapshotDocumentMapper {
    StoredAccessSnapshot snapshot(IngressSnapshot source, List<StoredAccessSnapshot.Endpoint> endpoints,
            Map<String, StoredAccessSnapshot.App> apps, Map<String, StoredAccessSnapshot.Client> clients,
            com.cloud.gateway.domain.capability.configuration.CapabilityComposition policies);
    StoredAccessSnapshot.App app(App source);
    StoredAccessSnapshot.Client client(Client source);
    StoredAccessSnapshot.Endpoint endpoint(Endpoint source);
}
