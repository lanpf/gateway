package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.gateway.api.enums.ClientVerificationRequirementApiEnum;
import com.cloud.gateway.api.enums.EndpointStatusApiEnum;
import com.cloud.gateway.api.enums.SubjectAuthenticationRequirementApiEnum;

import java.util.List;
import java.util.Set;

/**
 * Editing state of the endpoint-access definition. The sourceRevision is the shared CAS token of
 * the whole endpoint collection, not a per-endpoint version; a null revision means the definition
 * is not initialized yet.
 */
public record EndpointsApiView(String sourceRevision, Set<EndpointApiView> endpoints) {
    public EndpointsApiView {
        endpoints = endpoints == null ? null : Set.copyOf(endpoints);
    }

    public record EndpointApiView(
            String endpointId,
            String serviceId,
            String routeId,
            List<MatcherApiView> matchers,
            Set<String> tags,
            EndpointStatusApiEnum status,
            ClientVerificationRequirementApiEnum clientVerificationRequirement,
            SubjectAuthenticationRequirementApiEnum subjectAuthenticationRequirement,
            Set<String> requiredScopes,
            Set<String> requiredAudiences
    ) {
        public EndpointApiView {
            matchers = matchers == null ? null : List.copyOf(matchers);
            tags = tags == null ? null : Set.copyOf(tags);
            requiredScopes = requiredScopes == null ? null : Set.copyOf(requiredScopes);
            requiredAudiences = requiredAudiences == null ? null : Set.copyOf(requiredAudiences);
        }
    }

    public record MatcherApiView(String httpMethod, String pathPattern) { }
}
