package com.cloud.gateway.domain.security.signature;

import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.client.SignatureContract;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestSignatureVerifierRegistryTest {
    @Test
    void shouldResolveEveryRegisteredContract() {
        RequestSignatureVerifierRegistry registry = new RequestSignatureVerifierRegistry(
                Arrays.stream(SignatureContract.values()).map(value -> (RequestSignatureVerifier) new StubVerifier(value)).toList());
        for (SignatureContract contract : SignatureContract.values()) {
            assertEquals(contract, registry.required(contract).contract());
        }
    }

    @Test
    void shouldRejectMissingAndDuplicateContracts() {
        StubVerifier verifier = new StubVerifier(SignatureContract.HTTP_REQUEST_SIGNATURE_V1);
        assertThrows(DomainException.class, () -> new RequestSignatureVerifierRegistry(List.of(verifier)));
        assertThrows(DomainException.class, () -> new RequestSignatureVerifierRegistry(List.of(verifier, verifier)));
        assertThrows(DomainException.class, () -> new RequestSignatureVerifierRegistry(List.of()));
    }

    private record StubVerifier(SignatureContract contract) implements RequestSignatureVerifier {
        @Override
        public boolean verify(String secret, SignatureInput evidence) {
            return false;
        }
    }
}
