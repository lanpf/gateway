package com.cloud.gateway.application.configuration.editing;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;

import java.util.List;
import java.util.Set;

/** Content-only upsert of one endpoint's delivery identity and access access; status is never touched. */
public record SaveEndpointAccessCommand(Revision expectedSourceRevision,
                                        EndpointId endpointId,
                                        ServiceId serviceId,
                                        RouteId routeId,
                                        List<EndpointMatcher> matchers,
                                        Set<String> tags,
                                        ClientVerificationRequirement clientVerificationRequirement,
                                        SubjectAuthenticationRequirement subjectAuthenticationRequirement,
                                        Set<String> requiredScopes,
                                        Set<String> requiredAudiences) {
    public SaveEndpointAccessCommand {
        Require.notNull(matchers);
        matchers = NullSafe.copyOf(matchers);
        tags = tags == null ? Set.of() : NullSafe.copyOf(tags);
    }
}
