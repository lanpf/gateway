package com.cloud.gateway.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.common.DomainError;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointDefinition;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IngressPolicySnapshotTest {
    @Test
    void shouldRejectSnapshotWhoseActiveCredentialIsNotEffectiveAtPublication() {
        Instant publishedAt = Instant.parse("2026-08-24T00:00:00Z");
        RequestSignatureChannelCredential future = new RequestSignatureChannelCredential(
                new ChannelCredentialId("credential-1"), publishedAt.plusSeconds(60),
                publishedAt.plusSeconds(3600), new ChannelCode("PARTNER"),
                SignaturePayloadContract.SECRET_TIMESTAMP_BODY_HMAC_SHA256, "partner");
        IngressPolicyContent content = new IngressPolicyContent(catalog(),
                Map.of(new ClientAppId("app"), new IngressClientAppPolicy(
                        new ClientAppId("app"), ClientAppStatus.ENABLED, "APP", Set.of())),
                Map.of(new ChannelCode("PARTNER"), new IngressChannelAccessPolicy(
                        new ChannelCode("PARTNER"), Set.of(new ClientAppId("app")))),
                Map.of(new ChannelCode("PARTNER"), new IngressChannelCredentialPolicy(
                        new ChannelCode("PARTNER"), future, null)),
                Map.of(new EndpointId("LOGIN"), route()));

        DomainException exception = assertThrows(DomainException.class,
                () -> new IngressPolicySnapshot(1, content, "hash", publishedAt));

        assertEquals(DomainError.POLICY_SNAPSHOT_INVALID.getErrorCode(), exception.getErrorCode());
    }

    private static EndpointCatalog catalog() {
        return new EndpointCatalog(List.of(new EndpointDefinition(new EndpointId("LOGIN"), "service",
                List.of(new EndpointMatcher("POST", "/login")), "service")));
    }
    private static IngressRouteAccessPolicy route() {
        return new IngressRouteAccessPolicy(new EndpointId("LOGIN"), ChannelCredentialRequirement.IGNORE,
                ChannelRequirement.REQUIRED, SubjectRequirement.IGNORE, Set.of(), Set.of(),
                CredentialForwardingPolicy.stripAll(), true);
    }
}
