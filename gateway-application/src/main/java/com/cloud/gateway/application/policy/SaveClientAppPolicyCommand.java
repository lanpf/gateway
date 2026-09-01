package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.IngressClientAppPolicy;

public record SaveClientAppPolicyCommand(String expectedSourceRevision, IngressClientAppPolicy policy) { }
