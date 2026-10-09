package com.cloud.gateway.domain.capability.header;

import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.CapabilityConfigRule;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import java.util.List;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable deployment-owned name admission and operation restrictions. */
public record HeaderConfigRule(
        Set<String> denySet,
        Set<String> denyRemove
) implements CapabilityConfigRule<HeaderConfig> {
    public HeaderConfigRule {
        denySet = normalized(denySet);
        denyRemove = normalized(denyRemove);
    }

    private static Set<String> normalized(Set<String> names) {
        return NullSafe.copyOf(names)
                .stream().map(RequestHeader::normalizeName).collect(Collectors.toUnmodifiableSet());
    }

    public static HeaderConfigRule empty() {
        return new HeaderConfigRule(Set.of(), Set.of());
    }

    @Override
    public CapabilityId id() { return BuiltInCapabilityCatalog.HEADER_APPLY; }

    @Override
    public Class<HeaderConfig> configType() { return HeaderConfig.class; }

    @Override
    public void validate(List<HeaderConfig> configs) {
        Require.notNull(configs);
        NullSafe.forEach(configs, config -> {
            NullSafe.forEach(config.setHeaders().keySet(), header -> doValidate(header, denySet));
            NullSafe.forEach(config.removeHeaders(), header -> doValidate(header, denyRemove));
        });
    }

    private void doValidate(String header, Set<String> deny) {
        if (RequestHeader.fromName(header).isPresent() || deny.contains(RequestHeader.normalizeName(header))) {
            throw invalid();
        }
    }

    private static DomainException invalid() {
        return new DomainException(DomainError.ACCESS_SNAPSHOT_POLICY_INVALID);
    }
}
