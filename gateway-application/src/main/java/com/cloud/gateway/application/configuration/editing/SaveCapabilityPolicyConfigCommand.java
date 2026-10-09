package com.cloud.gateway.application.configuration.editing;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.capability.policy.CapabilityPolicyConfig;
public record SaveCapabilityPolicyConfigCommand(Revision expectedSourceRevision, CapabilityPolicyConfig capabilityPolicyConfig) { }
