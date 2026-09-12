package com.cloud.gateway.domain.policy.bundle;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;

import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public record PolicyBinding(
        PolicyBindingId bindingId,
        PolicyBundleId bundleId,
        String serviceId,
        EndpointId endpointId,
        Set<String> tags,
        AppId appId,
        ClientId verifiedClientId,
        SubjectType subjectType
) {
    public PolicyBinding {
        Require.notNull(bindingId, DomainException::missingField);
        Require.notNull(bundleId, DomainException::missingField);
        tags = tags == null ? Set.of() : tags;
        if (serviceId != null && StringUtils.isBlank(serviceId)
                || tags.stream().anyMatch(StringUtils::isBlank)) {
            throw DomainException.invalidField();
        }
        tags = Set.copyOf(tags);
    }

    public int specificity() {
        return endpointId != null ? 30 : !tags.isEmpty() ? 20 : serviceId != null ? 10 : 0;
    }

    public boolean matchesEndpoint(Endpoint endpoint) {
        return (serviceId == null || serviceId.equals(endpoint.serviceId()))
                && (endpointId == null || endpointId.equals(endpoint.endpointId()))
                && endpoint.tags().containsAll(tags);
    }

    public boolean matches(Endpoint endpoint, AppId app, ClientId client, SubjectType subject) {
        return matchesEndpoint(endpoint) && (appId == null || appId.equals(app))
                && (verifiedClientId == null || verifiedClientId.equals(client))
                && (subjectType == null || subjectType == subject);
    }

    public boolean overlapsContext(PolicyBinding other) {
        return compatible(appId, other.appId) && compatible(verifiedClientId, other.verifiedClientId)
                && compatible(subjectType, other.subjectType);
    }

    private static boolean compatible(Object left, Object right) {
        return left == null || right == null || left.equals(right);
    }
}
