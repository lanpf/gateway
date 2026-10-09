package com.cloud.gateway.infrastructure.policy.mapstruct;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.mapper.MapStructConfig;
import com.cloud.gateway.domain.capability.policy.*;
import com.cloud.gateway.domain.common.AbstractStringEntityId;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.infrastructure.policy.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.InjectionStrategy;
import com.cloud.framework.core.validation.Require;
import com.cloud.framework.core.util.NullSafe;
import java.util.Objects;
import com.cloud.framework.core.error.FrameworkException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper(config = MapStructConfig.class, uses = CapabilityConfigCodecs.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public abstract class AbstractCapabilityPolicyConfigStorageMapStructMapper implements CapabilityPolicyConfigStorageMapper {
    @Override public abstract StoredCapabilityPolicyConfig toStored(CapabilityPolicyConfig source);
    @Mapping(target = "capabilities", source = "configs")
    protected abstract StoredCapabilityPolicyConfig.Bundle bundle(CapabilityBundle source);
    protected abstract StoredCapabilityPolicyConfig.Binding binding(CapabilityBinding source);
    protected String identifier(AbstractStringEntityId source) { return source == null ? null : source.value(); }
    protected String serviceIdentifier(ServiceId source) { return source == null ? null : source.value(); }

    @Mapping(target = "configs", source = "capabilities")
    protected abstract CapabilityBundle domainBundle(StoredCapabilityPolicyConfig.Bundle source);
    protected CapabilityBundleId bundleId(String source) { return new CapabilityBundleId(source); }

    @Override public abstract CapabilityPolicyConfig toDomain(StoredCapabilityPolicyConfig source);

    /** Rejects stored-key mismatches and normalized key collisions before the domain constructor indexes. */
    protected Collection<CapabilityBundle> domainBundles(
            Map<String, StoredCapabilityPolicyConfig.Bundle> source) {
        if (source == null) {
            return List.of();
        }
        return List.copyOf(Require.noDuplicates(NullSafe.copyOf(source).entrySet().stream().map(entry -> {
            CapabilityBundle bundle = domainBundle(entry.getValue());
            if (!bundleId(entry.getKey()).equals(bundle.getBundleId())) {
                throw FrameworkException.invalidObject();
            }
            return bundle;
        }).toList(), CapabilityBundle::getBundleId, FrameworkException::invalidObject).values());
    }

    protected Collection<CapabilityBinding> domainBindings(
            Map<String, StoredCapabilityPolicyConfig.Binding> source) {
        if (source == null) {
            return List.of();
        }
        return List.copyOf(Require.noDuplicates(NullSafe.copyOf(source).entrySet().stream().map(entry -> {
            CapabilityBinding binding = domainBinding(entry.getValue());
            if (!new CapabilityBindingId(entry.getKey()).equals(binding.bindingId())) {
                throw FrameworkException.invalidObject();
            }
            return binding;
        }).toList(), CapabilityBinding::bindingId, FrameworkException::invalidObject).values());
    }

    protected CapabilityBinding domainBinding(StoredCapabilityPolicyConfig.Binding value) {
        return new CapabilityBinding(new CapabilityBindingId(value.bindingId()), new CapabilityBundleId(value.bundleId()),
                value.serviceId() == null ? null : new ServiceId(value.serviceId()),
                value.endpointId() == null ? null : new EndpointId(value.endpointId()),
                value.tags(), value.appId() == null ? null : new AppId(value.appId()),
                value.verifiedClientId() == null ? null : new ClientId(value.verifiedClientId()), value.subjectType());
    }
}
