package com.cloud.gateway.application.policy;
import com.cloud.gateway.domain.policy.bundle.PolicyComposition;
public record SavePolicyCompositionCommand(String expectedSourceRevision, PolicyComposition policies) { }
