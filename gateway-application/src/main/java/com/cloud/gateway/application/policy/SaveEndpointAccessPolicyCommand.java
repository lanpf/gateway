package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.endpoint.EndpointPolicy;

public record SaveEndpointAccessPolicyCommand(String expectedSourceRevision, EndpointPolicy policy) { }
