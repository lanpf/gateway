package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.client.ClientPolicy;

public record SaveClientAccessPolicyCommand(String expectedSourceRevision, ClientPolicy policy) { }
