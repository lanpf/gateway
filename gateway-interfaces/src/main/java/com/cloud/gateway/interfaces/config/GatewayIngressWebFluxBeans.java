package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.ingress.IngressAccessCommandService;
import com.cloud.gateway.domain.policy.bundle.PolicyResolver;
import com.cloud.gateway.application.governance.ResponseCache;
import com.cloud.gateway.application.governance.DownstreamCircuitBreaker;
import com.cloud.gateway.application.quota.QuotaCommandService;
import com.cloud.gateway.interfaces.webflux.RequestQuotaGuard;
import com.cloud.gateway.interfaces.webflux.DownstreamPolicyHandler;
import com.cloud.gateway.interfaces.webflux.PolicySnapshotWebFilter;
import com.cloud.gateway.interfaces.webflux.LocalPolicyWebFilter;
import com.cloud.gateway.interfaces.webflux.GatewayObservationWebFilter;
import io.micrometer.core.instrument.MeterRegistry;
import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.domain.access.IngressAccessEvaluator;
import com.cloud.gateway.interfaces.rest.ClientRequestBodyArgumentResolver;
import com.cloud.gateway.interfaces.webflux.BrowserSessionSubjectAuthenticator;
import com.cloud.gateway.interfaces.webflux.HostSessionSubjectAuthenticator;
import com.cloud.gateway.interfaces.webflux.TrustedRequestContextGlobalFilter;
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
    @Bean GatewayRouteContractValidator gatewayRouteContractValidator(PolicySnapshotProvider provider,
            org.springframework.cloud.gateway.route.RouteLocator routes) {
        return new GatewayRouteContractValidator(provider, routes);
    }
    @Bean
    IngressAccessEvaluator ingressAccessPolicyEvaluator() { return new IngressAccessEvaluator(); }
    @Bean IngressAccessCommandService ingressAccessCommandService(ClientCredentialAuthenticator authenticator,
                                                                  IngressAccessEvaluator evaluator, Clock clock) {
        return new IngressAccessCommandService(authenticator, evaluator, new PolicyResolver(), clock);
    }
    @Bean TrustedRequestContextGlobalFilter trustedRequestContextGlobalFilter(
            IngressAccessCommandService ingress,
            HostSessionSubjectAuthenticator hostSessionSubjectAuthenticator,
            BrowserSessionSubjectAuthenticator browserSessionSubjectAuthenticator,
            RequestQuotaGuard quotas, DownstreamPolicyHandler downstream) {
        return new TrustedRequestContextGlobalFilter(ingress,
                hostSessionSubjectAuthenticator, browserSessionSubjectAuthenticator, quotas, downstream);
    }

    @Bean RequestQuotaGuard requestQuotaGuard(QuotaCommandService quotas) {
        return new RequestQuotaGuard(quotas);
    }
    @Bean GatewayObservationWebFilter gatewayObservationWebFilter(ObjectProvider<MeterRegistry> registries) {
        return new GatewayObservationWebFilter(registries.getIfUnique());
    }
    @Bean DownstreamPolicyHandler downstreamPolicyHandler(ResponseCache cache, DownstreamCircuitBreaker circuits) {
        return new DownstreamPolicyHandler(cache, circuits);
    }
    @Bean PolicySnapshotWebFilter policySnapshotWebFilter(PolicySnapshotProvider provider, RequestQuotaGuard quotas) {
        return new PolicySnapshotWebFilter(provider, quotas);
    }
    @Bean LocalPolicyWebFilter localPolicyWebFilter(TrustedRequestContextGlobalFilter filter) {
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
