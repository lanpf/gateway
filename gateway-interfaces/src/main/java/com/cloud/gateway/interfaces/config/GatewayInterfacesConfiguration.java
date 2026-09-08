package com.cloud.gateway.interfaces.config;

import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.application.security.ChannelCredentialAuthenticator;
import com.cloud.gateway.domain.access.IngressAccessPolicyEvaluator;
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
public class GatewayInterfacesConfiguration {
    @Bean IngressAccessPolicyEvaluator ingressAccessPolicyEvaluator() { return new IngressAccessPolicyEvaluator(); }
    @Bean TrustedRequestContextGlobalFilter trustedRequestContextGlobalFilter(
            PolicySnapshotProvider provider,
            ChannelCredentialAuthenticator channelCredentialAuthenticator,
            HostSessionSubjectAuthenticator hostSessionSubjectAuthenticator,
            BrowserSessionSubjectAuthenticator browserSessionSubjectAuthenticator,
            IngressAccessPolicyEvaluator evaluator,
            Clock clock) {
        return new TrustedRequestContextGlobalFilter(provider, channelCredentialAuthenticator,
                hostSessionSubjectAuthenticator, browserSessionSubjectAuthenticator, evaluator, clock);
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
