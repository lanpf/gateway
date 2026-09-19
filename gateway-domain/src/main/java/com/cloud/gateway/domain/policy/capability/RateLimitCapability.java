package com.cloud.gateway.domain.policy.capability;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.policy.bundle.ResolvedCapabilities;
import com.cloud.gateway.domain.policy.execution.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rate-limit-specific facts and payloads; the ingress stages do not know dimensions or rule IDs. */
public final class RateLimitCapability implements CapabilityDefinition {
    public static final String ID = "rate-limit";

    @Override
    public String capability() { return ID; }

    @Override
    public Set<CapabilitySlot> slots() { return Set.of(CapabilitySlot.PRE_AUTH, CapabilitySlot.POST_AUTH); }

    @Override
    public CapabilityRepeat repeat() { return CapabilityRepeat.ONCE_PER_REQUEST; }

    @Override
    public List<Unit> units(ResolvedCapabilities resolved, CapabilityContext context) {
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
        return RateLimitEvaluator.applicable(resolved.rateLimits(), dimensions,
                        context.slot() == CapabilitySlot.PRE_AUTH, Set.of()).stream()
                .map(Unit::new).toList();
    }

    public record Unit(ApplicableQuota quota) implements CapabilityUnit {
        public Unit { Require.notNull(quota, DomainException::missingField); }
        @Override
        public String unitId() { return quota.rule().ruleId().value(); }

        @Override
        public Set<CapabilityFact> requiredFacts() {
            return switch (quota.rule().dimension()) {
                case IP -> Set.of(CapabilityFact.REQUEST);
                case APP, ENDPOINT -> Set.of(CapabilityFact.ACCESS_GRANTED);
                case CLIENT -> Set.of(CapabilityFact.VERIFIED_CLIENT);
                case SUBJECT -> Set.of(CapabilityFact.AUTHENTICATED_SUBJECT);
            };
        }
    }

    public record Outcome(RateLimitDecision decision) implements CapabilityOutcome {
        public Outcome { Require.notNull(decision, DomainException::missingField); }
        @Override
        public boolean allowed() { return decision.allowed(); }
    }
}
