package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;

public record SaveChannelAccessPolicyCommand(String expectedSourceRevision, IngressChannelAccessPolicy policy) { }
