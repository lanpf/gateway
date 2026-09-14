package com.cloud.gateway.api.facade.policy;
import com.cloud.framework.core.Request;
import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.api.enums.AuthorizationHeaderPolicyApiEnum;
import com.cloud.gateway.api.enums.CircuitBreakerFallbackApiEnum;
import com.cloud.gateway.api.enums.CookieForwardingModeApiEnum;
import com.cloud.gateway.api.enums.RateLimitDimensionApiEnum;
import com.cloud.gateway.api.enums.RateLimitModeApiEnum;
import com.cloud.gateway.api.enums.RedisFailurePolicyApiEnum;
import com.cloud.gateway.api.enums.SubjectTypeApiEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SavePolicyConfigApiCommand(@NotBlank String expectedSourceRevision,
        @NotNull @Valid SavePolicyConfigApiCommand.PolicyConfig policies) implements Request {

    /**
     * Typed v1 policy configuration payload; no domain types cross the API boundary.
     */
    public record PolicyConfig(
            @NotNull Map<@NotBlank String, @Valid Bundle> bundles,
            @NotNull List<@Valid Binding> bindings) {
        public PolicyConfig {
            bundles = bundles == null ? null : Map.copyOf(bundles);
            bindings = bindings == null ? null : List.copyOf(bindings);
        }

        public record Rule(
                @NotNull @NotBlank @Valid String ruleId,
                @NotNull @Valid RateLimitDimensionApiEnum dimension,
                @NotNull @Valid Integer capacity,
                @NotNull @Valid Integer refillPerSecond,
                @NotNull @Valid RateLimitModeApiEnum mode) {
        }

        public record RateLimit(
                @NotNull @Valid List<Rule> rules,
                @NotNull @Valid RedisFailurePolicyApiEnum onRedisFailure) {
            public RateLimit {
                rules = rules == null ? null : List.copyOf(rules);
            }
        }

        public record Timeout(
                @NotNull @Valid Duration connect,
                @NotNull @Valid Duration read,
                @NotNull @Valid Duration total) {
        }

        public record CircuitBreaker(
                @NotNull @Valid Integer failureRateThreshold,
                @NotNull @Valid Integer slowCallRateThreshold,
                @NotNull @Valid Duration slowCallDuration,
                @NotNull @Valid Integer minimumCalls,
                @NotNull @Valid Integer slidingWindowSize,
                @NotNull @Valid Integer halfOpenCalls,
                @NotNull @Valid Duration waitDuration,
                @NotNull @Valid CircuitBreakerFallbackApiEnum fallback) {
        }

        public record Cache(
                @NotNull @Valid Duration ttl,
                @NotNull @Valid Duration staleWindow,
                @NotNull @Valid Integer maxBodyBytes,
                @NotNull @Valid Set<String> varyHeaders) {
            public Cache {
                varyHeaders = varyHeaders == null ? null : Set.copyOf(varyHeaders);
            }
        }

        public record Headers(
                @NotNull Set<@NotNull RequestHeader> trustedHeaders,
                @NotNull @Valid Map<String, String> setHeaders,
                @NotNull @Valid Set<String> removeHeaders,
                @NotNull @Valid Forwarding credentialForwarding) {
            public Headers {
                trustedHeaders = trustedHeaders == null ? null : Set.copyOf(trustedHeaders);
                setHeaders = setHeaders == null ? null : Map.copyOf(setHeaders);
                removeHeaders = removeHeaders == null ? null : Set.copyOf(removeHeaders);
            }
        }

        public record Forwarding(
                @NotNull @Valid AuthorizationHeaderPolicyApiEnum authorizationHeader,
                @NotNull @Valid CookieForwardingModeApiEnum cookies,
                @NotNull @Valid Set<String> allowedCookieNames) {
            public Forwarding {
                allowedCookieNames = allowedCookieNames == null ? null : Set.copyOf(allowedCookieNames);
            }
        }

        public record Bundle(
                @NotNull @NotBlank @Valid String bundleId,
                @Valid RateLimit rateLimit,
                @Valid Timeout timeout,
                @Valid CircuitBreaker circuitBreaker,
                @Valid Cache cache,
                @Valid Headers headers) {
        }

        public record Binding(
                @NotNull @NotBlank @Valid String bindingId,
                @NotNull @NotBlank @Valid String bundleId,
                @Valid String serviceId,
                @Valid String endpointId,
                @NotNull @Valid Set<String> tags,
                @Valid String appId,
                @Valid String verifiedClientId,
                @Valid SubjectTypeApiEnum subjectType) {
            public Binding {
                tags = tags == null ? null : Set.copyOf(tags);
            }
        }

    }
}
