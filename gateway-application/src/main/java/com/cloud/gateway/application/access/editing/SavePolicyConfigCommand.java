package com.cloud.gateway.application.access.editing;
import com.cloud.gateway.domain.policy.bundle.PolicyConfig;
public record SavePolicyConfigCommand(String expectedSourceRevision, PolicyConfig policies) { }
