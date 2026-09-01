package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;

public record SaveRouteAccessPolicyCommand(String expectedSourceRevision, IngressRouteAccessPolicy policy) { }
