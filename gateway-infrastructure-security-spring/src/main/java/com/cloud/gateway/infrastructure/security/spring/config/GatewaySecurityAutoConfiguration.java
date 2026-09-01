package com.cloud.gateway.infrastructure.security.spring.config;

import com.cloud.gateway.api.constants.GatewayPathApiConstants;
import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.application.security.ChannelCredentialAuthenticator;
import com.cloud.gateway.application.security.ReplayGuard;
import com.cloud.gateway.application.security.SecretResolver;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import java.util.List;

import com.cloud.gateway.infrastructure.security.spring.signature.UnavailableBrowserSessionVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.LegacySecretParamsTimestampMd5UpperSignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.LegacySecretTimestampBodySha256SignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureChannelCredentialAuthenticator;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureVerifierRegistry;
import com.cloud.gateway.infrastructure.security.spring.signature.SecretTimestampBodyHmacSha256SignatureVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringReactiveOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;

@AutoConfiguration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityAutoConfiguration {
    private static final String ADMIN_SCOPE_AUTHORITY = "SCOPE_admin";

    @Bean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http, GatewaySecurityProperties properties) {
        http.authorizeExchange(authorize -> authorize
                        .pathMatchers(GatewayPathApiConstants.ADMIN_BASE_PATH + "/**").access((authentication, context) -> authentication
                                .map(value -> new AuthorizationDecision(hasAdminAccess(value, properties)))
                                .defaultIfEmpty(new AuthorizationDecision(false)))
                        .anyExchange().permitAll())
                .csrf(ServerHttpSecurity.CsrfSpec::disable);
        if (properties.getAccessToken().getFormat() == GatewaySecurityProperties.Format.REFERENCE) {
            http.oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(opaque -> { }));
        } else {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }));
        }
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = GatewaySecurityProperties.ACCESS_TOKEN_PREFIX,
            name = "format",
            havingValue = "SELF_CONTAINED")
    ReactiveJwtDecoder reactiveJwtDecoder(GatewaySecurityProperties properties) {
        GatewaySecurityProperties.AccessToken accessToken = properties.getAccessToken();
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(accessToken.getJwt().getJwkSetUri()).build();
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

    @Bean SecretResolver channelSecretResolver(GatewaySecurityProperties properties) {
        return new PropertySecretResolver(properties);
    }
    @Bean SmartInitializingSingleton channelCredentialSecretValidator(
            PolicySnapshotProvider provider, SecretResolver resolver) {
        return () -> provider.current().snapshot().content().channelCredentialPolicies().values().forEach(policy -> {
            resolver.resolve(policy.activeCredential().secretRef());
            policy.graceCredentialOptional().ifPresent(value -> resolver.resolve(value.secretRef()));
        });
    }
    @Bean ChannelCredentialAuthenticator channelCredentialAuthenticator(
            SecretResolver resolver,
            ReplayGuard replayGuard,
            RequestSignatureVerifierRegistry verifierRegistry,
            GatewaySecurityProperties properties
    ) {
        return new RequestSignatureChannelCredentialAuthenticator(
                resolver, replayGuard, verifierRegistry, properties.getRequestSignature().getMaxClockSkew());
    }
    @Bean
    RequestSignatureVerifier legacySecretParamsTimestampMd5UpperSignatureVerifier() {
        return new LegacySecretParamsTimestampMd5UpperSignatureVerifier();
    }
    @Bean RequestSignatureVerifier legacySecretTimestampBodySha256SignatureVerifier() {
        return new LegacySecretTimestampBodySha256SignatureVerifier();
    }
    @Bean RequestSignatureVerifier secretTimestampBodyHmacSha256SignatureVerifier() {
        return new SecretTimestampBodyHmacSha256SignatureVerifier();
    }
    @Bean RequestSignatureVerifierRegistry requestSignatureVerifierRegistry(
            List<RequestSignatureVerifier> verifiers) {
        return new RequestSignatureVerifierRegistry(verifiers);
    }
    @Bean @ConditionalOnMissingBean BrowserSessionVerifier browserSessionVerifier() {
        return new UnavailableBrowserSessionVerifier();
    }

    private static boolean hasAdminAccess(Authentication authentication, GatewaySecurityProperties properties) {
        boolean scopeAllowed = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_SCOPE_AUTHORITY.equals(authority.getAuthority()));
        if (!scopeAllowed || !(authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> token)) return false;
        Object audiences = token.getTokenAttributes().get(JwtClaimNames.AUD);
        if (audiences instanceof java.util.Collection<?> collection) {
            return collection.stream().map(String::valueOf).anyMatch(properties.getAdminAudience()::equals);
        }
        return properties.getAdminAudience().equals(String.valueOf(audiences));
    }
}
