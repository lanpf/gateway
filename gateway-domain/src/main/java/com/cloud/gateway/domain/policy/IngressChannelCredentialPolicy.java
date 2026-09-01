package com.cloud.gateway.domain.policy;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import java.util.Optional;

public record IngressChannelCredentialPolicy(
        ChannelCode channelCode,
        RequestSignatureChannelCredential activeCredential,
        RequestSignatureChannelCredential graceCredential
) {
    public IngressChannelCredentialPolicy {
        Require.notNull(channelCode, DomainException::missingField);
        Require.notNull(activeCredential, DomainException::missingField);
        if (!channelCode.equals(activeCredential.channelCode())) {
            throw invalid();
        }
        if (graceCredential != null) {
            if (!channelCode.equals(graceCredential.channelCode())) {
                throw invalid();
            }
            if (activeCredential.credentialId().equals(graceCredential.credentialId())) {
                throw invalid();
            }
            if (activeCredential.signaturePayloadContract() != graceCredential.signaturePayloadContract()) {
                throw invalid();
            }
        }
    }

    public Optional<RequestSignatureChannelCredential> graceCredentialOptional() {
        return Optional.ofNullable(graceCredential);
    }

    private static DomainException invalid() {
        return DomainException.invalidField();
    }
}
