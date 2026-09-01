package com.cloud.gateway.application.security;

import com.cloud.gateway.domain.access.ChannelCredentialAuthenticationResult;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

public interface ChannelCredentialAuthenticator {
    CompletionStage<ChannelCredentialAuthenticationResult> authenticate(
            IngressChannelCredentialPolicy policy,
            ChannelSignatureEvidence evidence,
            Instant now);
}
