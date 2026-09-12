package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndpointPathPatternValidatorTest {
    @Test
    void shouldRejectInvalidPathGrammarBeforeItCanBePublished() {
        var mapper = new StoredPolicySnapshotMapper();
        for (String path : List.of("relative", "/api/**/tail", "/api/{broken")) {
            assertThrows(InfrastructureException.class, () -> mapper.toStoredEndpoints(registry(path)));
        }
    }

    @Test
    void shouldAcceptTransportTemplatesAndTrailingWildcard() {
        var mapper = new StoredPolicySnapshotMapper();
        for (String path : List.of("/api/{endpointId}", "/api/**")) {
            assertDoesNotThrow(() -> mapper.toDomainEndpointRegistry(mapper.toStoredEndpoints(registry(path))));
        }
    }

    private static EndpointRegistry registry(String path) {
        return new EndpointRegistry(List.of(new Endpoint(new EndpointId("API.GET"), "api",
                List.of(new EndpointMatcher("GET", path)), "api")));
    }
}
