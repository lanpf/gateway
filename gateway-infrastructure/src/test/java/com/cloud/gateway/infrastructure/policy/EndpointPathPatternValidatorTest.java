package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.infrastructure.policy.DefaultStoredAccessSnapshotMapper;
import com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityCompositionStorageMapStructMapperImpl;
import com.cloud.gateway.infrastructure.policy.mapstruct.AccessSnapshotDocumentMapStructMapperImpl;

import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointAccess;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.endpoint.RouteId;
import com.cloud.gateway.domain.endpoint.ServiceId;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndpointPathPatternValidatorTest {
    @Test
    void shouldRejectInvalidPathGrammarBeforeItCanBePublished() {
        var mapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))));
        for (String path : List.of("relative", "/api/**/tail", "/api/{broken")) {
            assertThrows(InfrastructureException.class, () -> mapper.toStoredEndpoints(registry(path)));
        }
    }

    @Test
    void shouldAcceptTransportTemplatesAndTrailingWildcard() {
        var mapper = new DefaultStoredAccessSnapshotMapper(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper())),
                new AccessSnapshotDocumentMapStructMapperImpl(new CapabilityCompositionStorageMapStructMapperImpl(com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration.builtIns(com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityConfigs.registry(), new com.cloud.gateway.infrastructure.policy.mapstruct.CapabilityNodeStorageMapStructMapperImpl(), new com.fasterxml.jackson.databind.ObjectMapper()))));
        for (String path : List.of("/api/{endpointId}", "/api/**")) {
            assertDoesNotThrow(() -> mapper.toDomainEndpointRegistry(mapper.toStoredEndpoints(registry(path))));
        }
    }

    private static EndpointRegistry registry(String path) {
        return new EndpointRegistry(List.of(new Endpoint(new EndpointId("API.GET"), new ServiceId("api"),
                new RouteId("api"), List.of(new EndpointMatcher("GET", path)), Set.of(),
                EndpointStatus.ENABLED,
                new EndpointAccess(ClientVerificationRequirement.REQUIRED,
                        SubjectAuthenticationRequirement.ANONYMOUS, Set.of(), Set.of()))));
    }
}
