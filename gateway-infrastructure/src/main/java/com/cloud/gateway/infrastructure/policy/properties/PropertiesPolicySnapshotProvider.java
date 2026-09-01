package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.policy.ChannelAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.ChannelCredentialPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.ClientAppPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.EndpointCatalogDefinitionRepository;
import com.cloud.gateway.application.policy.PolicySnapshotHasher;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.policy.RouteAccessPolicyDefinitionRepository;
import com.cloud.gateway.application.policy.VersionedPolicySnapshot;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import java.time.Clock;

public final class PropertiesPolicySnapshotProvider implements PolicySnapshotProvider {
    private final VersionedPolicySnapshot current;

    public PropertiesPolicySnapshotProvider(
            EndpointCatalogDefinitionRepository endpointCatalog,
            ClientAppPolicyDefinitionRepository clientApps,
            ChannelAccessPolicyDefinitionRepository channelAccess,
            ChannelCredentialPolicyDefinitionRepository channelCredentials,
            RouteAccessPolicyDefinitionRepository routeAccess,
            PolicySnapshotHasher hasher,
            Clock clock
    ) {
        IngressPolicyContent content = new IngressPolicyContent(endpointCatalog.load().value(),
                clientApps.load().value(), channelAccess.load().value(), channelCredentials.load().value(),
                routeAccess.load().value());
        String contentHash = hasher.hash(content);
        current = new VersionedPolicySnapshot(
                new IngressPolicySnapshot(0, content, contentHash, clock.instant()),
                "properties:" + contentHash);
    }

    @Override
    public VersionedPolicySnapshot current() {
        return current;
    }
}
