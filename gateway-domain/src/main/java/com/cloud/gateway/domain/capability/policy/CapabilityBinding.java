package com.cloud.gateway.domain.capability.policy;

import com.cloud.framework.core.SubjectType;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.ServiceId;

import java.util.Set;

public record CapabilityBinding(
        CapabilityBindingId bindingId,
        CapabilityBundleId bundleId,
        ServiceId serviceId,
        EndpointId endpointId,
        Set<String> tags,
        AppId appId,
        ClientId verifiedClientId,
        SubjectType subjectType
) {
    public CapabilityBinding {
        Require.allNotNull(bindingId, bundleId);
        tags = NullSafe.copyOf(tags, Require::notBlank);
    }

    public int specificity() {
        return endpointId != null ? 30 : !tags.isEmpty() ? 20 : serviceId != null ? 10 : 0;
    }

    public boolean matchesEndpoint(Endpoint endpoint) {
        return (serviceId == null || serviceId.equals(endpoint.getServiceId()))
                && (endpointId == null || endpointId.equals(endpoint.getId()))
                && endpoint.getTags().containsAll(tags);
    }

    public boolean matches(Endpoint endpoint, AppId app, ClientId client, SubjectType subject) {
        return matchesEndpoint(endpoint)
                && (appId == null || appId.equals(app))
                && (verifiedClientId == null || verifiedClientId.equals(client))
                && (subjectType == null || subjectType == subject);
    }

    public boolean overlaps(CapabilityBinding other) {
        return compatible(appId, other.appId)
                && compatible(verifiedClientId, other.verifiedClientId)
                && compatible(subjectType, other.subjectType);
    }

    private static boolean compatible(Object left, Object right) {
        return left == null || right == null || left.equals(right);
    }
}
