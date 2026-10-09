package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.domain.capability.CapabilityConfigRule;
import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import com.cloud.gateway.domain.capability.ratelimit.EffectiveRateLimitPolicy;

import com.cloud.gateway.domain.capability.header.EffectiveHeaderPolicy;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;

import com.cloud.gateway.application.ingress.CapabilityExecutionCommand;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyAssembler;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfigValidator;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyComposer;
import com.cloud.gateway.domain.capability.header.HeaderPolicyComposer;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitPolicyComposer;
import com.cloud.gateway.domain.capability.resilience.TimeoutPolicyComposer;
import java.util.concurrent.atomic.AtomicInteger;

import com.cloud.gateway.application.ingress.CapabilityExecutionCommandService;
import com.cloud.gateway.application.ingress.CapabilityExecutionOutput;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.application.quota.QuotaObserver;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.capability.policy.EffectiveCapabilityPolicies;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitMode;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitConfig;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitRule;
import com.cloud.gateway.domain.capability.resilience.TimeoutConfig;
import com.cloud.gateway.domain.capability.runtime.CapabilityContext;
import com.cloud.gateway.domain.capability.runtime.CapabilitySlot;
import com.cloud.gateway.domain.capability.runtime.HeaderApplyCapability;
import com.cloud.gateway.domain.capability.runtime.RateLimitCapability;
import com.cloud.gateway.domain.capability.runtime.TimeoutCapability;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.VerifiedApp;
import com.cloud.gateway.interfaces.webflux.RequestQuotaGuard;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual wiring without starting Spring or invoking external quota storage. */
class CapabilityAssemblyIT {
    @Test
    void shouldUseTheInjectedAssemblerForRequestValidationAndPolicyConfig() {
        AtomicInteger validations = new AtomicInteger();
        List<CapabilityPolicyComposer<?, ?>> composers = List.of(
                RateLimitPolicyComposer.INSTANCE, TimeoutPolicyComposer.INSTANCE, HeaderPolicyComposer.INSTANCE);
        CapabilityPolicyConfigValidator validator =
                CapabilityPolicyConfigValidator.forComposers(composers,
                        List.of(new CapabilityConfigRule<HeaderConfig>() {
                            public com.cloud.gateway.domain.capability.CapabilityId id() { return BuiltInCapabilityCatalog.HEADER_APPLY; }
                            public Class<HeaderConfig> configType() { return HeaderConfig.class; }
                            public void validate(List<HeaderConfig> configs) { validations.incrementAndGet(); }
                        }), Set.of(BuiltInCapabilityCatalog.RATE_LIMIT));
        CapabilityPolicyAssembler assembler = new CapabilityPolicyAssembler(composers, validator);
        RequestQuotaGuard quotas = new RequestQuotaGuard(new QuotaCommandService(
                (rule, key) -> { throw new AssertionError("No quota is configured"); },
                (rule, key) -> { throw new AssertionError("No quota is configured"); }, QuotaObserver.noop()));
        CapabilityExecutionCommandService service = new IngressWebFluxConfiguration()
                .capabilityExecutionCommandService(quotas, assembler);
        service.execute(new CapabilityExecutionCommand(CapabilityPolicyConfig.empty(),
                new CapabilityContext(CapabilitySlot.PRE_AUTH,
                        new RequestEvidence("GET", "/read", null, null, null, null, "ip"), null, null), Set.of()))
                .toCompletableFuture().join();
        assertEquals(1, validations.get());
    }

    @Test
    void shouldWireEveryCapabilityAndItsTypedConfigurationSource() {
        IngressWebFluxConfiguration wiring = new IngressWebFluxConfiguration();
        java.util.List<RateLimitRule> acquired = new java.util.ArrayList<>();
        RequestQuotaGuard quotas = new RequestQuotaGuard(new QuotaCommandService(
                (rule, key) -> {
                    acquired.add(rule);
                    return com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision.allowed(null, null, 1);
                },
                (rule, key) -> { throw new AssertionError("FORWARD must not acquire local quota"); },
                QuotaObserver.noop()));
        CapabilityExecutionCommandService service = wiring.capabilityExecutionCommandService(quotas, BuiltInCapabilityCatalog.assembler());
        RateLimitRule rule = new RateLimitRule("ip", RateLimitDimension.IP, 10, 1, RateLimitMode.EXACT);
        TimeoutConfig timeout = new TimeoutConfig(Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(3));
        HeaderConfig headers = HeaderConfig.defaults(null);
        EffectiveCapabilityPolicies config = new EffectiveCapabilityPolicies(java.util.List.of(new EffectiveCapabilityPolicies.Value<>(EffectiveRateLimitPolicy.class, new EffectiveRateLimitPolicy(List.of(new RateLimitConfig(List.of(rule), null)))), new EffectiveCapabilityPolicies.Value<>(EffectiveTimeoutPolicy.class, new EffectiveTimeoutPolicy(timeout)), new EffectiveCapabilityPolicies.Value<>(EffectiveHeaderPolicy.class, new EffectiveHeaderPolicy(headers))));
        RequestEvidence evidence = new RequestEvidence("GET", "/read", null, null, null, null, "ip");
        CapabilityExecutionOutput preAuth = service.executeAgainst(config,
                new CapabilityContext(CapabilitySlot.PRE_AUTH, evidence, null, null), Set.of())
                .toCompletableFuture().join();
        assertEquals(1, preAuth.execution().outcomes().size());
        assertInstanceOf(RateLimitCapability.Outcome.class, preAuth.execution().outcomes().get(0));
        assertEquals(List.of(rule), acquired);

        Endpoint endpoint = new Endpoint(new EndpointId("read"), new ServiceId("service"),
                new RouteId("route"), List.of(new EndpointMatcher("GET", "/read")), Set.of(),
                EndpointStatus.ENABLED, new EndpointAccess(ClientVerificationRequirement.OPTIONAL,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()));
        AccessGrantedContext granted = new AccessGrantedContext(new VerifiedApp(new AppId("app"), "web"),
                null, null);
        CapabilityExecutionOutput output = service.executeAgainst(config,
                new CapabilityContext(CapabilitySlot.FORWARD, evidence, endpoint, granted), preAuth.execution().executed())
                .toCompletableFuture().join();
        assertSame(config, output.capabilities());
        assertEquals(2, output.execution().outcomes().size());
        assertSame(timeout, assertInstanceOf(TimeoutCapability.Outcome.class,
                output.execution().outcomes().get(0)).policy().config());
        assertSame(headers, assertInstanceOf(HeaderApplyCapability.Outcome.class,
                output.execution().outcomes().get(1)).policy().config());
        assertEquals(List.of(rule), acquired);
    }
}
