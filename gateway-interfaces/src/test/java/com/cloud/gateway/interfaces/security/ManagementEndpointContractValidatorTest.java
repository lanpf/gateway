package com.cloud.gateway.interfaces.security;

import com.cloud.gateway.application.policy.VersionedPolicySnapshot;
import com.cloud.gateway.domain.access.IngressAccessContent;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.security.ManagementAccessRules;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManagementEndpointContractValidatorTest {
    @Test
    void shouldValidateSameAudienceAndScopeForLocalManagementRoute() {
        assertDoesNotThrow(() -> validator("gateway-admin", "/custom", SubjectAuthencationRequirement.HOST_SESSION_REQUIRED,
                Set.of("admin"), Set.of("gateway")).afterSingletonsInstantiated());
        assertThrows(DomainException.class, () -> validator("gateway-admin", "/custom", SubjectAuthencationRequirement.HOST_SESSION_REQUIRED,
                Set.of("read"), Set.of("gateway")).afterSingletonsInstantiated());
        assertThrows(DomainException.class, () -> validator("gateway-admin", "/custom", SubjectAuthencationRequirement.HOST_SESSION_REQUIRED,
                Set.of("admin"), Set.of("other")).afterSingletonsInstantiated());
        assertThrows(DomainException.class, () -> validator("gateway-admin", "/custom", SubjectAuthencationRequirement.HOST_SESSION_OPTIONAL,
                Set.of("admin"), Set.of("gateway")).afterSingletonsInstantiated());
    }

    @Test
    void shouldAlsoProtectManagementPathAndNotApplyAdminRulesToOrdinaryRoutes() {
        assertThrows(DomainException.class, () -> validator("route", "/admin/gateway/snapshot", SubjectAuthencationRequirement.IGNORE,
                Set.of(), Set.of()).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> validator("route", "/public", SubjectAuthencationRequirement.IGNORE,
                Set.of(), Set.of()).afterSingletonsInstantiated());
    }

    private static ManagementEndpointContractValidator validator(String route, String path, SubjectAuthencationRequirement requirement,
            Set<String> scopes, Set<String> audiences) {
        Endpoint endpoint = new Endpoint(new EndpointId("ADMIN.TEST"), "gateway",
                List.of(new EndpointMatcher("GET", path)), route);
        EndpointPolicy policy = new EndpointPolicy(endpoint.endpointId(), ClientVerificationRequirement.OPTIONAL,
                ClientVerificationMode.IGNORE, requirement, scopes, audiences,
                CredentialForwarding.stripAll(), EndpointStatus.ENABLED);
        IngressAccessSnapshot snapshot = new IngressAccessSnapshot(1,
                new IngressAccessContent(new EndpointRegistry(List.of(endpoint)), Map.of(), Map.of(), Map.of(endpoint.endpointId(), policy)),
                "hash", Instant.EPOCH);
        return new ManagementEndpointContractValidator(() -> new VersionedPolicySnapshot(snapshot, "revision"), new ManagementAccessRules("gateway"));
    }
}
