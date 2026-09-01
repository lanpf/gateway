package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointId;
import java.util.Set;

public record IngressRouteAccessPolicy(
        EndpointId endpointId,
        ChannelCredentialRequirement channelCredentialRequirement,
        ChannelRequirement channelRequirement,
        SubjectRequirement subjectRequirement,
        Set<String> requiredScopes,
        Set<String> requiredAudiences,
        CredentialForwardingPolicy credentialForwardingPolicy,
        boolean enabled
) {
    public IngressRouteAccessPolicy {
        Require.notNull(endpointId, DomainException::missingField);
        Require.notNull(channelCredentialRequirement, DomainException::missingField);
        Require.notNull(channelRequirement, DomainException::missingField);
        Require.notNull(subjectRequirement, DomainException::missingField);
        requiredScopes = Set.copyOf(Require.notNull(requiredScopes, DomainException::missingField));
        requiredAudiences = Set.copyOf(Require.notNull(requiredAudiences, DomainException::missingField));
        Require.notNull(credentialForwardingPolicy, DomainException::missingField);
        if (channelCredentialRequirement == ChannelCredentialRequirement.REQUEST_SIGNATURE
                && channelRequirement != ChannelRequirement.REQUIRED) {
            throw invalid();
        }
        if (channelRequirement == ChannelRequirement.FORBIDDEN
                && channelCredentialRequirement != ChannelCredentialRequirement.IGNORE) {
            throw invalid();
        }
        boolean host = subjectRequirement == SubjectRequirement.HOST_SESSION_REQUIRED
                || subjectRequirement == SubjectRequirement.HOST_SESSION_OPTIONAL;
        if (!host && !requiredScopes.isEmpty()) {
            throw invalid();
        }
        if (!host && !requiredAudiences.isEmpty()) {
            throw invalid();
        }
        if (host && requiredAudiences.isEmpty()) {
            throw invalid();
        }
    }

    private static DomainException invalid() {
        return DomainException.invalidField();
    }
}
