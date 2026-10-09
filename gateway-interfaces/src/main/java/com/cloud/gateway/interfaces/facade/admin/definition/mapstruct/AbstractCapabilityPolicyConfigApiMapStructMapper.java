package com.cloud.gateway.interfaces.facade.admin.definition.mapstruct;

import com.cloud.framework.core.mapper.MapStructConfig;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.api.facade.admin.definition.SaveCapabilityPolicyConfigApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveCapabilityPolicyConfigApiCommand.CapabilityPolicyConfigApiCommand;
import com.cloud.gateway.domain.capability.policy.*;
import com.cloud.gateway.application.configuration.editing.SaveCapabilityPolicyConfigCommand;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.framework.core.validation.Require;
import com.cloud.framework.core.util.NullSafe;
import java.util.Objects;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.cloud.gateway.infrastructure.policy.CapabilityConfigCodecs;
import com.cloud.gateway.interfaces.facade.admin.definition.CapabilityPolicyConfigApiMapper;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class, uses = CapabilityConfigCodecs.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public abstract class AbstractCapabilityPolicyConfigApiMapStructMapper implements CapabilityPolicyConfigApiMapper {
    @Override
    public SaveCapabilityPolicyConfigCommand toCommand(SaveCapabilityPolicyConfigApiCommand source) {
        try {
            return command(source);
        } catch (InfrastructureException exception) {
            if (!exception.getErrorCode().equals(InfrastructureError.INFRA_TECH_POLICY_DESERIALIZATION_FAILED.errorCode())) {
                throw exception;
            }
            throw FrameworkException.invalidObject();
        } catch (IllegalArgumentException exception) {
            throw FrameworkException.invalidObject();
        }
    }

    @Mapping(target = "capabilityPolicyConfig", source = "policies")
    protected abstract SaveCapabilityPolicyConfigCommand command(SaveCapabilityPolicyConfigApiCommand source);

    protected abstract CapabilityPolicyConfig policyConfig(CapabilityPolicyConfigApiCommand source);
    @Mapping(target = "configs", source = "capabilities", qualifiedByName = "capabilityNodes")
    protected abstract CapabilityBundle bundle(CapabilityPolicyConfigApiCommand.BundleApiCommand source);
    protected abstract CapabilityBinding binding(CapabilityPolicyConfigApiCommand.BindingApiCommand source);

    /** Preserves normalized-key collision rejection at the protocol boundary before the domain constructor indexes. */
    protected Collection<CapabilityBundle> bundles(
            Map<String, CapabilityPolicyConfigApiCommand.BundleApiCommand> source) {
        return List.copyOf(Require.noDuplicates(NullSafe.copyOf(Objects.requireNonNull(source, "configuration")).entrySet().stream().map(entry -> {
            CapabilityBundle bundle = bundle(entry.getValue());
            if (!bundleId(entry.getKey()).equals(bundle.getBundleId())) {
                throw FrameworkException.invalidObject();
            }
            return bundle;
        }).toList(), CapabilityBundle::getBundleId, FrameworkException::invalidObject).values());
    }

    protected Collection<CapabilityBinding> bindings(
            Map<String, CapabilityPolicyConfigApiCommand.BindingApiCommand> source) {
        return List.copyOf(Require.noDuplicates(NullSafe.copyOf(Objects.requireNonNull(source, "configuration")).entrySet().stream().map(entry -> {
            CapabilityBinding binding = binding(entry.getValue());
            if (!bindingId(entry.getKey()).equals(binding.bindingId())) {
                throw FrameworkException.invalidObject();
            }
            return binding;
        }).toList(), CapabilityBinding::bindingId, FrameworkException::invalidObject).values());
    }

    protected Revision revision(String value) { return new Revision(value); }
    protected CapabilityBundleId bundleId(String value) { return new CapabilityBundleId(value); }
    protected CapabilityBindingId bindingId(String value) { return new CapabilityBindingId(value); }
    protected AppId appId(String value) { return value == null ? null : new AppId(value); }
    protected ClientId clientId(String value) { return value == null ? null : new ClientId(value); }
    protected ServiceId serviceId(String value) { return value == null ? null : new ServiceId(value); }
    protected EndpointId endpointId(String value) { return value == null ? null : new EndpointId(value); }
}
