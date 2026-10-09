package com.cloud.gateway.domain.capability.runtime;

import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;


import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.capability.CapabilityId;
import com.cloud.gateway.domain.capability.ratelimit.ApplicableQuota;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDecision;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitDimension;
import com.cloud.gateway.domain.capability.ratelimit.RateLimitEvaluator;
import com.cloud.gateway.domain.capability.ratelimit.EffectiveRateLimitPolicy;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rate-limit-specific facts and payloads; the ingress stages do not know dimensions or rule IDs. */
public final class RateLimitCapability implements PlannableCapability<EffectiveRateLimitPolicy> {
    public static final CapabilityId ID = BuiltInCapabilityCatalog.RATE_LIMIT;

    @Override
    public CapabilityId id() { return ID; }

    @Override
    public Set<CapabilitySlot> slots() { return Set.of(CapabilitySlot.PRE_AUTH, CapabilitySlot.POST_AUTH); }

    @Override
    public CapabilityRepeat repeat() { return CapabilityRepeat.ONCE_PER_REQUEST; }

    /** Consumes only its own node: the resolved rate-limit groups, never the rest of the policy configuration. */
    @Override
    public List<Unit> units(EffectiveRateLimitPolicy policy, CapabilityContext context) {
        Require.allNotNull(policy, context);
        Map<RateLimitDimension, String> dimensions = new EnumMap<>(RateLimitDimension.class);
        dimensions.put(RateLimitDimension.IP, context.evidence().remoteIp());
        AccessGrantedContext granted = context.granted();
        if (granted != null) {
            dimensions.put(RateLimitDimension.APP, granted.app().appId().value());
            dimensions.put(RateLimitDimension.ENDPOINT, context.endpoint().getId().value());
            if (granted.client() != null) {
                dimensions.put(RateLimitDimension.CLIENT, granted.client().clientId().value());
            }
            if (granted.subject() != null) {
                dimensions.put(RateLimitDimension.SUBJECT,
                        granted.subject().subjectType().name() + ":" + granted.subject().subjectId().value());
            }
        }
        return RateLimitEvaluator.applicable(policy.configs(), dimensions,
                        context.slot() == CapabilitySlot.PRE_AUTH, Set.of()).stream()
                .map(Unit::new).toList();
    }

    public record Unit(ApplicableQuota quota) implements CapabilityUnit {
        public Unit { Require.notNull(quota); }
        @Override
        public String unitId() { return quota.rule().ruleId().value(); }

        @Override
        public Set<CapabilityFact> requiredFacts() {
            return switch (quota.rule().dimension()) {
                case IP -> Set.of();
                case APP, ENDPOINT -> Set.of(CapabilityFact.ACCESS_GRANTED);
                case CLIENT -> Set.of(CapabilityFact.VERIFIED_CLIENT);
                case SUBJECT -> Set.of(CapabilityFact.AUTHENTICATED_SUBJECT);
            };
        }
    }

    public record Outcome(RateLimitDecision decision) implements CapabilityOutcome {
        public Outcome { Require.notNull(decision); }
        @Override
        public Disposition disposition() {
            return decision.allowed() ? Disposition.CONTINUE : Disposition.STOP;
        }

    }
}
