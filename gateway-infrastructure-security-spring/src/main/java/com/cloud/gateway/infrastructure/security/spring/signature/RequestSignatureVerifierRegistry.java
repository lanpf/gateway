package com.cloud.gateway.infrastructure.security.spring.signature;

import com.cloud.gateway.domain.policy.SignaturePayloadContract;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public final class RequestSignatureVerifierRegistry {
    private final Map<SignaturePayloadContract, RequestSignatureVerifier> verifiers;

    public RequestSignatureVerifierRegistry(List<RequestSignatureVerifier> verifiers) {
        Assert.notEmpty(verifiers, "RequestSignatureVerifier list must not be empty");
        Map<SignaturePayloadContract, RequestSignatureVerifier> indexed =
                new EnumMap<>(SignaturePayloadContract.class);
        verifiers.forEach(verifier -> {
            Assert.notNull(verifier, "RequestSignatureVerifier must not be null");
            RequestSignatureVerifier previous = indexed.putIfAbsent(verifier.contract(), verifier);
            Assert.isNull(previous,
                    () -> "Duplicate SignaturePayloadContract registration: " + verifier.contract());
        });
        EnumSet<SignaturePayloadContract> missing = EnumSet.allOf(SignaturePayloadContract.class);
        missing.removeAll(indexed.keySet());
        Assert.isTrue(CollectionUtils.isEmpty(missing),
                () -> "Missing RequestSignatureVerifier for SignaturePayloadContract: " + missing);
        this.verifiers = Map.copyOf(indexed);
    }

    public RequestSignatureVerifier required(SignaturePayloadContract contract) {
        RequestSignatureVerifier verifier = verifiers.get(contract);
        Assert.notNull(verifier,
                () -> "Missing RequestSignatureVerifier for SignaturePayloadContract: " + contract);
        return verifier;
    }
}
