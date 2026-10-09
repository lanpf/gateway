package com.cloud.gateway.domain.endpoint;

import com.cloud.framework.core.validation.Require;
import com.cloud.framework.domain.AggregateRoot;
import lombok.Getter;
import com.cloud.framework.core.util.NullSafe;
import java.util.List;
import java.util.Set;

/** Immutable aggregate; transitions preserve identity. */
@Getter
public final class Endpoint implements AggregateRoot<EndpointId> {
    private final EndpointId id;
    private final ServiceId serviceId;
    private final RouteId routeId;
    private final List<EndpointMatcher> matchers;
    private final Set<String> tags;
    private final EndpointStatus status;
    private final EndpointAccess access;

    public Endpoint(EndpointId id, ServiceId serviceId, RouteId routeId, List<EndpointMatcher> matchers, Set<String> tags, EndpointStatus status, EndpointAccess access) {
        Require.allNotNull(id, serviceId, routeId, status, access);
        this.id = id;
        this.serviceId = serviceId;
        this.routeId = routeId;
        this.matchers = NullSafe.copyOf(Require.notEmpty(matchers));
        this.tags = NullSafe.copyOf(tags, Require::notBlank);
        this.status = status;
        this.access = access;
    }


    public static Endpoint create(EndpointId id, ServiceId serviceId, RouteId routeId,
                                  List<EndpointMatcher> matchers, Set<String> tags, EndpointAccess access) {
        return new Endpoint(id, serviceId, routeId, matchers, tags, EndpointStatus.ENABLED, access);
    }

    public Endpoint edit(ServiceId serviceId, RouteId routeId, List<EndpointMatcher> matchers,
                         Set<String> tags, EndpointAccess access) {
        return new Endpoint(id, serviceId, routeId, matchers, tags, status, access);
    }

    public Endpoint changeStatus(EndpointStatus status) {
        return new Endpoint(id, serviceId, routeId, matchers, tags, status, access);
    }

    /** Transport candidates must agree on configuration as well as identity. */
    public boolean sameDefinition(Endpoint other) {
        return other != null && id.equals(other.id)
                && serviceId.equals(other.serviceId) && routeId.equals(other.routeId)
                && matchers.equals(other.matchers) && tags.equals(other.tags)
                && status == other.status && access.equals(other.access);
    }

    public boolean enabled() { return status == EndpointStatus.ENABLED; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Endpoint that && id.equals(that.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
