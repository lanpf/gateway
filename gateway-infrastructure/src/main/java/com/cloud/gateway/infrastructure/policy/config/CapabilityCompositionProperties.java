package com.cloud.gateway.infrastructure.policy.config;

import com.cloud.gateway.domain.policy.capability.*;
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.SubjectType;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

/** JavaBean binding containers; conversion and domain validation occur before publication. */
@Getter
public final class CapabilityCompositionProperties {
    @Valid private final Map<String, Bundle> bundles = new LinkedHashMap<>();
    @Valid private final List<Binding> bindings = new ArrayList<>();

    @Getter @Setter
    public static final class Rule {
        @Valid private String ruleId;
        @Valid private RateLimitDimension dimension;
        @Valid private Integer capacity;
        @Valid private Integer refillPerSecond;
        @Valid private RateLimitMode mode;
    }

    @Getter @Setter
    public static final class RateLimit {
        @Valid private final List<Rule> rules = new ArrayList<>();
        @Valid private RedisFailurePolicy onRedisFailure;
    }

    @Getter @Setter
    public static final class Timeout {
        @Valid private Duration connect;
        @Valid private Duration read;
        @Valid private Duration total;
    }

    @Getter @Setter
    public static final class CircuitBreaker {
        @Valid private Integer failureRateThreshold;
        @Valid private Integer slowCallRateThreshold;
        @Valid private Duration slowCallDuration;
        @Valid private Integer minimumCalls;
        @Valid private Integer slidingWindowSize;
        @Valid private Integer halfOpenCalls;
        @Valid private Duration waitDuration;
        @Valid private CircuitBreakerPolicy.Fallback fallback;
    }

    @Getter @Setter
    public static final class Cache {
        @Valid private Duration ttl;
        @Valid private Duration staleWindow;
        @Valid private Integer maxBodyBytes;
        @Valid private final Set<String> varyHeaders = new LinkedHashSet<>();
    }

    @Getter @Setter
    public static final class Headers {
        @Valid private final Set<RequestHeader> trustedHeaders = new LinkedHashSet<>();
        @Valid private final Map<String, String> setHeaders = new LinkedHashMap<>();
        @Valid private final Set<String> removeHeaders = new LinkedHashSet<>();
        @Valid private Forwarding credentialForwarding;
    }

    @Getter @Setter
    public static final class Forwarding {
        @Valid private AuthorizationHeaderForwardingMode authorizationHeader;
        @Valid private CookieForwardingMode cookies;
        @Valid private final Set<String> allowedCookieNames = new LinkedHashSet<>();
    }

    @Getter @Setter
    public static final class Bundle {
        @Valid private String bundleId;
        @Valid private RateLimit rateLimit;
        @Valid private Timeout timeout;
        @Valid private CircuitBreaker circuitBreaker;
        @Valid private Cache cache;
        @Valid private Headers headers;
    }

    @Getter @Setter
    public static final class Binding {
        @Valid private String bindingId;
        @Valid private String bundleId;
        @Valid private String serviceId;
        @Valid private String endpointId;
        @Valid private final Set<String> tags = new LinkedHashSet<>();
        @Valid private String appId;
        @Valid private String verifiedClientId;
        @Valid private SubjectType subjectType;
    }
}
