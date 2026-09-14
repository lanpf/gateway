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
public record EndpointsApiView(String sourceRevision, Set<Endpoint> endpoints) {

    public record Endpoint(
            String endpointId,
            String serviceId,
            String routeId,
            List<Matcher> matchers,
            Set<String> tags,
            EndpointStatusApiEnum status,
            ClientVerificationRequirementApiEnum clientVerificationRequirement,
            SubjectAuthenticationRequirementApiEnum subjectAuthenticationRequirement,
            Set<String> requiredScopes,
            Set<String> requiredAudiences
    ) { }

    public record Matcher(String httpMethod, String pathPattern) { }
}
