package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.domain.capability.resilience.EffectiveTimeoutPolicy;

import com.cloud.gateway.domain.capability.ratelimit.EffectiveRateLimitPolicy;

import com.cloud.gateway.domain.capability.header.EffectiveHeaderPolicy;

import com.cloud.gateway.domain.capability.policy.CapabilityPolicyAssembler;
import com.cloud.gateway.domain.capability.CapabilityId;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.Assert;


import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.interfaces.webflux.GatewayRouteContractValidator;

import com.cloud.gateway.application.ingress.AccessStageCommandService;
import com.cloud.gateway.application.ingress.AdmissionStageCommandService;
import com.cloud.gateway.application.ingress.IngressAdmissionCommandService;
import com.cloud.gateway.application.ingress.PreparationStageCommandService;
import com.cloud.gateway.application.ingress.PreparationObserver;
import com.cloud.gateway.domain.capability.runtime.RateLimitCapability;
import com.cloud.gateway.domain.capability.runtime.TimeoutCapability;
import com.cloud.gateway.application.ingress.CapabilityExecutionCommandService;
import com.cloud.gateway.application.ingress.CapabilityExecutor;
import com.cloud.gateway.application.ingress.CapabilityRegistration;
import com.cloud.gateway.application.ingress.HeaderApplyCapabilityExecutor;
import com.cloud.gateway.application.ingress.RateLimitCapabilityExecutor;
import com.cloud.gateway.application.ingress.TimeoutCapabilityExecutor;
import com.cloud.gateway.domain.capability.runtime.HeaderApplyCapability;
import com.cloud.gateway.interfaces.webflux.CapabilityResponseWriter;
import com.cloud.gateway.interfaces.webflux.RateLimitOutcomeWriter;
import java.util.List;
import com.cloud.gateway.interfaces.webflux.SnapshotEndpointMatchSource;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.interfaces.webflux.RequestQuotaGuard;
import com.cloud.gateway.interfaces.webflux.IngressPreparationWebFilter;
import com.cloud.gateway.interfaces.webflux.LocalPolicyWebFilter;
import com.cloud.gateway.interfaces.webflux.GatewayObservationWebFilter;
import io.micrometer.core.instrument.MeterRegistry;
import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.domain.ingress.access.AccessEvaluator;
import com.cloud.gateway.interfaces.rest.ClientRequestBodyArgumentResolver;
import com.cloud.gateway.interfaces.subject.BrowserSessionSubjectAuthenticator;
import com.cloud.gateway.interfaces.subject.HostSessionSubjectAuthenticator;
import com.cloud.gateway.interfaces.webflux.IngressAdmissionGlobalFilter;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

@Configuration(proxyBeanMethods = false)
public class IngressWebFluxConfiguration {
    @Bean GatewayRouteContractValidator gatewayRouteContractValidator(AccessSnapshotCapturer provider,
            RouteLocator routes) {
        return new GatewayRouteContractValidator(provider, routes);
    }
    @Bean
    AccessEvaluator ingressAccessPolicyEvaluator() { return new AccessEvaluator(); }
    @Bean AccessStageCommandService ingressAccessStageCommandService(ClientCredentialAuthenticator authenticator,
                                                                  AccessEvaluator evaluator, Clock clock) {
        return new AccessStageCommandService(authenticator, evaluator, clock);
    }
    @Bean CapabilityExecutionCommandService capabilityExecutionCommandService(RequestQuotaGuard quotas, CapabilityPolicyAssembler assembler) {
        List<CapabilityRegistration<?>> registrations = List.of(
                new CapabilityRegistration<>(new RateLimitCapability(), policies -> policies.get(EffectiveRateLimitPolicy.class)),
                new CapabilityRegistration<>(new TimeoutCapability(), policies -> policies.get(EffectiveTimeoutPolicy.class)),
                new CapabilityRegistration<>(new HeaderApplyCapability(), policies -> policies.get(EffectiveHeaderPolicy.class)));
        List<CapabilityExecutor<?>> executors = List.of(new RateLimitCapabilityExecutor(quotas),
                new TimeoutCapabilityExecutor(), new HeaderApplyCapabilityExecutor());
        Set<String> configured = assembler.capabilities().stream().map(CapabilityId::value).collect(Collectors.toSet());
        Set<String> planned = registrations.stream().map(registration -> registration.capability().id().value()).collect(Collectors.toSet());
        Assert.isTrue(configured.equals(planned), "Capability configuration and runtime registrations must match");
        // Reject missing planning entries or executors before accepting requests.
        return new CapabilityExecutionCommandService(assembler, registrations, executors);
    }
    @Bean CapabilityResponseWriter capabilityResponseWriter(RequestQuotaGuard quotas) {
        return new CapabilityResponseWriter(List.of(new RateLimitOutcomeWriter(quotas)));
    }
    @Bean AdmissionStageCommandService admissionStageCommandService(
            CapabilityExecutionCommandService capabilities) {
        return new AdmissionStageCommandService(capabilities);
    }
    @Bean IngressAdmissionCommandService ingressAdmissionCommandService(
            AccessStageCommandService access, AdmissionStageCommandService admission) {
        return new IngressAdmissionCommandService(access, admission);
    }
    @Bean IngressAdmissionGlobalFilter ingressAdmissionGlobalFilter(
            IngressAdmissionCommandService ingress,
            HostSessionSubjectAuthenticator hostSessionSubjectAuthenticator,
            BrowserSessionSubjectAuthenticator browserSessionSubjectAuthenticator,
            CapabilityResponseWriter responses,
            CapabilityExecutionCommandService capabilities) {
        return new IngressAdmissionGlobalFilter(ingress,
                hostSessionSubjectAuthenticator, browserSessionSubjectAuthenticator, responses, capabilities);
    }

    @Bean RequestQuotaGuard requestQuotaGuard(QuotaCommandService quotas) {
        return new RequestQuotaGuard(quotas);
    }
    @Bean GatewayObservationWebFilter gatewayObservationWebFilter(ObjectProvider<MeterRegistry> registries) {
        return new GatewayObservationWebFilter(registries.getIfUnique());
    }
    @Bean PreparationStageCommandService preparationStageCommandService(AccessSnapshotCapturer capturer,
                                                                        CapabilityExecutionCommandService capabilities, PreparationObserver observer) {
        return new PreparationStageCommandService(capturer, new SnapshotEndpointMatchSource(),
                capabilities, observer);
    }
    @Bean IngressPreparationWebFilter ingressPreparationWebFilter(
            PreparationStageCommandService preparation, CapabilityResponseWriter responses) {
        return new IngressPreparationWebFilter(preparation, responses);
    }

    @Bean com.cloud.gateway.application.ingress.PreparationObserver preparationObserver(
            ObjectProvider<MeterRegistry> registries) {
        MeterRegistry registry = registries.getIfUnique();
        if (registry == null) {
            return () -> { };
        }
        return new com.cloud.gateway.interfaces.webflux.MicrometerPreparationObserver(registry);
    }
    @Bean LocalPolicyWebFilter localPolicyWebFilter(IngressAdmissionGlobalFilter filter) {
        return new LocalPolicyWebFilter(filter);
    }

    @Bean
    HostSessionSubjectAuthenticator hostSessionSubjectAuthenticator() {
        return new HostSessionSubjectAuthenticator();
    }

    @Bean
    BrowserSessionSubjectAuthenticator browserSessionSubjectAuthenticator(BrowserSessionVerifier verifier) {
        return new BrowserSessionSubjectAuthenticator(verifier);
    }

    @Bean
    ClientRequestBodyArgumentResolver clientRequestBodyArgumentResolver(
            ServerCodecConfigurer codecConfigurer, ReactiveAdapterRegistry reactiveAdapterRegistry) {
        return new ClientRequestBodyArgumentResolver(codecConfigurer.getReaders(), reactiveAdapterRegistry);
    }

    @Bean
    WebFluxConfigurer gatewayWebFluxConfigurer(
            ObjectProvider<ClientRequestBodyArgumentResolver> resolverProvider) {
        // 延迟解析：resolver 依赖的 ServerCodecConfigurer 由 WebFlux 自动配置提供，
        // 直接注入会与 EnableWebFluxConfiguration 的创建形成循环依赖。
        return new WebFluxConfigurer() {
            @Override
            public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
                configurer.addCustomResolver(resolverProvider.getObject());
            }
        };
    }
}
