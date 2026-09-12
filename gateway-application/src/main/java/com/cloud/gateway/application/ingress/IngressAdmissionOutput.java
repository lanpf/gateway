package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.policy.capability.RateLimitDecision;

/** Denied access never executes quota; an admitted request always carries its quota decision. */
public record IngressAdmissionOutput(IngressAccessOutput access, RateLimitDecision quota) { }
