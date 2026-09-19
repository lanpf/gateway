package com.cloud.gateway.application.configuration.editing;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.policy.bundle.CapabilityComposition;
public record SaveCapabilityCompositionCommand(Revision expectedSourceRevision, CapabilityComposition policyConfig) { }
