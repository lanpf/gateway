package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.application.ingress.AccessStageCommandService;
import com.cloud.gateway.application.ingress.AdmissionStageCommandService;
import com.cloud.gateway.application.ingress.IngressAdmissionCommandService;
import com.cloud.gateway.application.ingress.PreparationStageCommandService;
import com.cloud.gateway.application.ingress.PreparationObserver;
import com.cloud.gateway.domain.policy.bundle.CapabilityResolver;
import com.cloud.gateway.domain.policy.execution.CapabilityPlanner;
import com.cloud.gateway.domain.policy.capability.RateLimitCapability;
import com.cloud.gateway.application.ingress.CapabilityExecutionCommandService;
import com.cloud.gateway.application.ingress.RateLimitCapabilityExecutor;
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
import com.cloud.gateway.interfaces.webflux.BrowserSessionSubjectAuthenticator;
import com.cloud.gateway.interfaces.webflux.HostSessionSubjectAuthenticator;
import com.cloud.gateway.interfaces.webflux.IngressAdmissionGlobalFilter;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

@Configuration(proxyBeanMethods = false)
public class GatewayIngressWebFluxBeans {
    @Bean GatewayRouteContractValidator gatewayRouteContractValidator(com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer provider,
            org.springframework.cloud.gateway.route.RouteLocator routes) {
        return new GatewayRouteContractValidator(provider, routes);
    }
    @Bean
    AccessEvaluator ingressAccessPolicyEvaluator() { return new AccessEvaluator(); }
    @Bean AccessStageCommandService ingressAccessStageCommandService(ClientCredentialAuthenticator authenticator,
                                                                  AccessEvaluator evaluator, Clock clock) {
        return new AccessStageCommandService(authenticator, evaluator, clock);
    }
    @Bean CapabilityPlanner capabilityPlanner() {
        return new CapabilityPlanner(List.of(new RateLimitCapability()));
    }
    @Bean CapabilityExecutionCommandService capabilityExecutionCommandService(RequestQuotaGuard quotas,
            CapabilityPlanner planner) {
        return new CapabilityExecutionCommandService(new CapabilityResolver(), planner,
                List.of(new RateLimitCapabilityExecutor(quotas)));
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
            CapabilityResponseWriter responses) {
        return new IngressAdmissionGlobalFilter(ingress,
                hostSessionSubjectAuthenticator, browserSessionSubjectAuthenticator, responses);
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
