package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import java.util.List;
import java.util.Set;

/** Content-only upsert of one endpoint's delivery identity and access policy; status is never touched. */
public record SaveEndpointAccessCommand(String expectedSourceRevision, EndpointId endpointId,
                                        ServiceId serviceId, RouteId routeId,
                                        List<EndpointMatcher> matchers, Set<String> tags,
                                        EndpointAccess policy) {
    public SaveEndpointAccessCommand {
        matchers = List.copyOf(matchers);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
