package com.cloud.gateway.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.framework.core.error.BaseError;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.coordination.redis.CoordinationRedisError;
import com.cloud.gateway.infrastructure.persistence.nacos.PersistenceNacosError;
import com.cloud.gateway.infrastructure.security.spring.SecuritySpringError;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Lives in gateway-boot because only this module sees every gateway error enum; the concrete
 * infrastructure modules must not depend on each other.
 */
class GatewayErrorCodeUniquenessTest {
    @Test
    void shouldKeepCompleteErrorCodesUniqueWithinGateway() {
        Set<String> codes = new HashSet<>();

        Stream.of(DomainError.values(), ApplicationError.values(), InfrastructureError.values(),
                        SecuritySpringError.values(), CoordinationRedisError.values(), PersistenceNacosError.values())
                .flatMap(Stream::of)
                .map(BaseError.class::cast)
                .forEach(error -> assertTrue(codes.add(error.getErrorCode()),
                        () -> "Duplicate gateway error code: " + error.getErrorCode()));
    }

    @Test
    void shouldKeepPublishedNacosErrorCodesStable() {
        assertEquals("800604", ApplicationError.APP_POLICY_SNAPSHOT_HISTORY_NOT_FOUND.getErrorCode());
        assertEquals("800703", InfrastructureError.INFRA_TECH_POLICY_DESERIALIZATION_FAILED.getErrorCode());
        assertEquals("800704", InfrastructureError.INFRA_TECH_POLICY_LAST_KNOWN_GOOD_FAILED.getErrorCode());
        assertEquals("800802", PersistenceNacosError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE.getErrorCode());
        assertEquals("800803", InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID.getErrorCode());
    }
}
