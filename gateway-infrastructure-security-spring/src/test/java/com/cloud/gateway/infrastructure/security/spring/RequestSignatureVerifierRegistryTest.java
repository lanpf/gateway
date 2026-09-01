package com.cloud.gateway.infrastructure.security.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.domain.policy.SignaturePayloadContract;
import java.util.List;

import com.cloud.gateway.infrastructure.security.spring.signature.LegacySecretParamsTimestampMd5UpperSignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.LegacySecretTimestampBodySha256SignatureVerifier;
import com.cloud.gateway.infrastructure.security.spring.signature.RequestSignatureVerifierRegistry;
import com.cloud.gateway.infrastructure.security.spring.signature.SecretTimestampBodyHmacSha256SignatureVerifier;
import org.junit.jupiter.api.Test;

class RequestSignatureVerifierRegistryTest {
    @Test
    void shouldResolveEveryRegisteredPayloadContract() {
        RequestSignatureVerifierRegistry registry = completeRegistry();

        for (SignaturePayloadContract contract : SignaturePayloadContract.values()) {
            assertEquals(contract, registry.required(contract).contract());
        }
    }

    @Test
    void shouldRejectMissingPayloadContractVerifier() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new RequestSignatureVerifierRegistry(List.of(
                        new SecretTimestampBodyHmacSha256SignatureVerifier())));
        assertTrue(exception.getMessage().startsWith("Missing RequestSignatureVerifier"));
    }

    @Test
    void shouldRejectDuplicatePayloadContractVerifier() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new RequestSignatureVerifierRegistry(List.of(
                        new LegacySecretParamsTimestampMd5UpperSignatureVerifier(),
                        new LegacySecretTimestampBodySha256SignatureVerifier(),
                        new SecretTimestampBodyHmacSha256SignatureVerifier(),
                        new SecretTimestampBodyHmacSha256SignatureVerifier())));
        assertTrue(exception.getMessage().startsWith("Duplicate SignaturePayloadContract"));
    }

    private static RequestSignatureVerifierRegistry completeRegistry() {
        return new RequestSignatureVerifierRegistry(List.of(
                new LegacySecretParamsTimestampMd5UpperSignatureVerifier(),
                new LegacySecretTimestampBodySha256SignatureVerifier(),
                new SecretTimestampBodyHmacSha256SignatureVerifier()));
    }
}
