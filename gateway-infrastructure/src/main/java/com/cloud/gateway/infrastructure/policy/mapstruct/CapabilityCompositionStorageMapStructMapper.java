package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.framework.core.mapper.MapStructConfig;
import com.cloud.gateway.domain.capability.configuration.*;
import com.cloud.gateway.domain.common.StringEntityId;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.infrastructure.policy.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.InjectionStrategy;

@Mapper(config = MapStructConfig.class, uses = CapabilityConfigCodecs.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public abstract class CapabilityCompositionStorageMapStructMapper implements CapabilityCompositionStorageMapper {
    @Override public abstract StoredCapabilityComposition toStored(CapabilityComposition source);
    @Mapping(target = "capabilities", source = "configurations")
    protected abstract StoredCapabilityComposition.Bundle bundle(CapabilityBundle source);
    protected abstract StoredCapabilityComposition.Binding binding(CapabilityBinding source);
    protected String identifier(StringEntityId source) { return source == null ? null : source.value(); }
    protected String serviceIdentifier(ServiceId source) { return source == null ? null : source.value(); }

    @Mapping(target = "configurations", source = "capabilities")
    protected abstract CapabilityBundle domainBundle(StoredCapabilityComposition.Bundle source);
    protected CapabilityBundleId bundleId(String source) { return new CapabilityBundleId(source); }

    @Override public abstract CapabilityComposition toDomain(StoredCapabilityComposition source);

    protected CapabilityBinding domainBinding(StoredCapabilityComposition.Binding value) {
        return new CapabilityBinding(new CapabilityBindingId(value.bindingId()), new CapabilityBundleId(value.bundleId()),
                value.serviceId() == null ? null : new ServiceId(value.serviceId()),
                value.endpointId() == null ? null : new EndpointId(value.endpointId()),
                value.tags(), value.appId() == null ? null : new AppId(value.appId()),
                value.verifiedClientId() == null ? null : new ClientId(value.verifiedClientId()), value.subjectType());
    }
}
