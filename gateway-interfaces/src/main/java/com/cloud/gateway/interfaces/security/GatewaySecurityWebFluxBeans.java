package com.cloud.gateway.interfaces.security;

import com.cloud.gateway.api.constants.GatewayPathApiConstants;
import com.cloud.gateway.domain.security.ManagementAccessRules;
import com.cloud.gateway.application.policy.PolicySnapshotProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewaySecurityEntryProperties.class)
public class GatewaySecurityWebFluxBeans {
    private static final String DESCENDANTS_PATTERN = "/**";

    @Bean
    ManagementAccessRules managementAccessRules(GatewaySecurityEntryProperties properties) {
        return new ManagementAccessRules(properties.getAdminAudience());
    }

    @Bean
    ManagementEndpointContractValidator managementEndpointContractValidator(PolicySnapshotProvider provider,
            ManagementAccessRules rules) {
        return new ManagementEndpointContractValidator(provider, rules);
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http,
            ManagementAccessRules rules, ObjectProvider<ReactiveJwtDecoder> decoders,
            ObjectProvider<ReactiveOpaqueTokenIntrospector> introspectors) {
        ManagementAuthorizationManager management = new ManagementAuthorizationManager(rules);
        http.authorizeExchange(authorize -> authorize
                .pathMatchers(GatewayPathApiConstants.ADMIN_BASE_PATH + DESCENDANTS_PATTERN).access(management)
                .anyExchange().permitAll()).csrf(ServerHttpSecurity.CsrfSpec::disable);
        ReactiveJwtDecoder decoder = decoders.getIfAvailable();
        ReactiveOpaqueTokenIntrospector introspector = introspectors.getIfAvailable();
        Assert.isTrue((decoder == null) != (introspector == null), "Exactly one token verification adapter must be configured");
        PolicyAwareBearerTokenConverter converter = new PolicyAwareBearerTokenConverter();
        if (introspector != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenConverter(converter)
                    .opaqueToken(opaque -> opaque.introspector(introspector)));
        } else {
            http.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenConverter(converter)
                    .jwt(jwt -> jwt.jwtDecoder(decoder)));
        }
        return http.build();
    }
}
