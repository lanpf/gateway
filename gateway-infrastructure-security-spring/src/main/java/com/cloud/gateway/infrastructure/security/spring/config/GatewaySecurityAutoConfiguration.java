package com.cloud.gateway.infrastructure.security.spring.config;

import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.application.security.ReplayGuard;
import com.cloud.gateway.application.security.SecretResolver;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;

import com.cloud.gateway.domain.security.signature.LegacySecretParamsTimestampMd5UpperSignatureVerifier;
import com.cloud.gateway.domain.security.signature.LegacySecretTimestampBodySha256SignatureVerifier;
import com.cloud.gateway.application.security.RequestSignatureAuthenticationCommandService;
import com.cloud.gateway.domain.security.signature.RequestSignatureVerifier;
import com.cloud.gateway.domain.security.signature.RequestSignatureVerifierRegistry;
import com.cloud.gateway.domain.security.signature.SecretTimestampBodyHmacSha256SignatureVerifier;
import com.cloud.gateway.domain.security.signature.HttpRequestSignatureV1Verifier;
import com.cloud.gateway.infrastructure.security.spring.session.BrowserSessionVerifierAdapter;
import java.time.Clock;
import com.cloud.gateway.domain.security.signature.SignatureCryptography;
import com.cloud.gateway.domain.security.signature.SignatureAuthenticationRules;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureCryptography;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringReactiveOpaqueTokenIntrospector;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = GatewaySecurityProperties.ACCESS_TOKEN_PREFIX,
            name = "format",
            havingValue = "SELF_CONTAINED")
    ReactiveJwtDecoder reactiveJwtDecoder(GatewaySecurityProperties properties) {
        GatewaySecurityProperties.AccessToken accessToken = properties.getAccessToken();
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(accessToken.getJwt().getSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(accessToken.getIssuer())));
        return decoder;
    }

    @Bean
    @ConditionalOnProperty(prefix = GatewaySecurityProperties.ACCESS_TOKEN_PREFIX,
            name = "format",
            havingValue = "REFERENCE")
    ReactiveOpaqueTokenIntrospector reactiveOpaqueTokenIntrospector(GatewaySecurityProperties properties) {
        GatewaySecurityProperties.Reference reference = properties.getAccessToken().getReference();
        return SpringReactiveOpaqueTokenIntrospector.withIntrospectionUri(reference.getIntrospectionUri())
                .clientId(reference.getClientId()).clientSecret(reference.getClientSecret()).build();
    }

    @Bean
    SignatureCryptography signatureCryptography() {
        return new RequestSignatureCryptography();
    }

    @Bean
    SecretResolver clientSecretResolver(GatewaySecurityProperties properties) {
        return new PropertySecretResolver(properties);
    }
    @Bean
    SmartInitializingSingleton clientCredentialSecretValidator(
            PolicySnapshotProvider provider, SecretResolver resolver) {
        return () -> provider.current().snapshot().content().clients().values().forEach(policy -> {
            if (policy.credentials() == null) {
                return;
            }
            resolver.resolve(policy.credentials().activeCredential().secretRef());
            policy.credentials().graceCredentialOptional().ifPresent(value -> resolver.resolve(value.secretRef()));
        });
    }
    @Bean
    ClientCredentialAuthenticator clientCredentialAuthenticator(
            SecretResolver resolver,
            ReplayGuard replayGuard,
            RequestSignatureVerifierRegistry verifierRegistry,
            SignatureCryptography cryptography,
            GatewaySecurityProperties properties
    ) {
        return new RequestSignatureAuthenticationCommandService(
                resolver, replayGuard, verifierRegistry, cryptography,
                new SignatureAuthenticationRules(properties.getRequestSignature().getMaxClockSkew()));
    }
    @Bean
    RequestSignatureVerifier legacySecretParamsTimestampMd5UpperSignatureVerifier(SignatureCryptography cryptography) {
        return new LegacySecretParamsTimestampMd5UpperSignatureVerifier(cryptography);
    }
    @Bean
    RequestSignatureVerifier legacySecretTimestampBodySha256SignatureVerifier(SignatureCryptography cryptography) {
        return new LegacySecretTimestampBodySha256SignatureVerifier(cryptography);
    }
    @Bean
    RequestSignatureVerifier secretTimestampBodyHmacSha256SignatureVerifier(SignatureCryptography cryptography) {
        return new SecretTimestampBodyHmacSha256SignatureVerifier(cryptography);
    }
    @Bean
    RequestSignatureVerifier httpRequestSignatureV1Verifier(SignatureCryptography cryptography) {
        return new HttpRequestSignatureV1Verifier(cryptography);
    }
    @Bean
    RequestSignatureVerifierRegistry requestSignatureVerifierRegistry(
            ObjectProvider<RequestSignatureVerifier> verifiers) {
        return new RequestSignatureVerifierRegistry(verifiers.stream().toList());
    }
    @Bean
    @ConditionalOnMissingBean(BrowserSessionVerifier.class)
    BrowserSessionVerifier browserSessionVerifier(
            WebClient.Builder webClientBuilder,
            ReactorLoadBalancerExchangeFilterFunction loadBalancerExchangeFilter,
            GatewaySecurityProperties properties,
            Clock clock
    ) {
        String serviceBaseUrl = "http://" + properties.getBrowserSession().getServiceId();
        return new BrowserSessionVerifierAdapter(
                webClientBuilder.clone()
                        .baseUrl(serviceBaseUrl)
                        .filter(loadBalancerExchangeFilter)
                        .build(),
                clock);
    }

}
