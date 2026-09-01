package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;

public record SaveChannelCredentialPolicyCommand(String expectedSourceRevision, IngressChannelCredentialPolicy policy) { }
