package com.cloud.gateway.domain.client;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.framework.core.error.FrameworkException;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CredentialsTest {

    @Test void shouldAcceptActiveOnlyAndDistinctGraceIdentity() {
        Credential active = credential("active", SignatureContract.HTTP_REQUEST_SIGNATURE_V1);
        Credential grace = credential("grace", SignatureContract.HTTP_REQUEST_SIGNATURE_V1);
        assertTrue(new Credentials(active, null).graceCredentialOptional().isEmpty());
        var pair = new Credentials(active, grace);
        assertEquals(active, pair.activeCredential());
        assertEquals(grace, pair.graceCredentialOptional().orElseThrow());
    }

    @Test void shouldRejectMissingActiveIncludingGraceOnly() {
        assertThrows(FrameworkException.class, () -> new Credentials(null, null));
        assertThrows(FrameworkException.class, () -> new Credentials(null,
                credential("grace", SignatureContract.HTTP_REQUEST_SIGNATURE_V1)));
    }

    @Test void shouldRejectDuplicateCredentialIdentity() {
        Credential active = credential("active", SignatureContract.HTTP_REQUEST_SIGNATURE_V1);
        assertThrows(DomainException.class, () -> new Credentials(active, active));
    }

    @Test void shouldRejectSignatureProtocolMigrationInsideRotationPair() {
        assertThrows(DomainException.class, () -> new Credentials(
                credential("active", SignatureContract.HTTP_REQUEST_SIGNATURE_V1),
                credential("grace", SignatureContract.LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256)));
    }

    private static Credential credential(String id, SignatureContract contract) {
        return new Credential(new CredentialId(id), "secret-reference", contract,
                Instant.EPOCH, Instant.parse("2099-01-01T00:00:00Z"));
    }
}
