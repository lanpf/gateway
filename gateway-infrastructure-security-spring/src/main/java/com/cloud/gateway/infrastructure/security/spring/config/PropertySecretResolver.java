package com.cloud.gateway.infrastructure.security.spring.config;

import com.cloud.gateway.application.security.SecretResolver;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public final class PropertySecretResolver implements SecretResolver {
    private final GatewaySecurityProperties properties;
    @Override public String resolve(String secretRef) {
        String secret = properties.getRequestSignature().getSecrets().get(secretRef);
        if (!StringUtils.hasText(secret)) {
            throw new InfrastructureException(InfrastructureError.INFRA_TECH_SECRET_NOT_FOUND);
        }
        return secret;
    }
}
