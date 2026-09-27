package com.cloud.gateway.api.facade.admin.definition;
import com.cloud.framework.core.Request;
import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.api.enums.AuthorizationHeaderPolicyApiEnum;
import com.cloud.gateway.api.enums.CookieForwardingModeApiEnum;
import com.cloud.gateway.api.enums.RateLimitDimensionApiEnum;
import com.cloud.gateway.api.enums.RateLimitModeApiEnum;
import com.cloud.gateway.api.enums.RedisFailureModeApiEnum;
import com.cloud.gateway.api.enums.SubjectTypeApiEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SaveCapabilityCompositionApiCommand(@NotBlank String expectedSourceRevision,
        @NotNull @Valid SaveCapabilityCompositionApiCommand.CapabilityCompositionApiCommand policies) implements Request {

    /**
     * Typed v1 policy configuration payload; no domain types cross the API boundary.
     */
    public record CapabilityCompositionApiCommand(
            @NotNull Map<@NotBlank String, @Valid BundleApiCommand> bundles,
            @NotNull List<@Valid BindingApiCommand> bindings) {
        public CapabilityCompositionApiCommand {
            bundles = bundles == null ? null : Map.copyOf(bundles);
            bindings = bindings == null ? null : List.copyOf(bindings);
        }

        public record RuleApiCommand(
                @NotNull @NotBlank @Valid String ruleId,
                @NotNull @Valid RateLimitDimensionApiEnum dimension,
                @NotNull @Valid Integer capacity,
                @NotNull @Valid Integer refillPerSecond,
                @NotNull @Valid RateLimitModeApiEnum mode) {
        }

        public record RateLimitApiCommand(
                @NotNull @Valid List<RuleApiCommand> rules,
                @NotNull @Valid RedisFailureModeApiEnum onRedisFailure) {
            public RateLimitApiCommand {
                rules = rules == null ? null : List.copyOf(rules);
            }
        }

        public record TimeoutApiCommand(
                @NotNull @Valid Duration connect,
                @NotNull @Valid Duration read,
                @NotNull @Valid Duration total) {
        }

        public record HeadersApiCommand(
                @NotNull Set<@NotNull RequestHeader> trustedHeaders,
                @NotNull @Valid Map<String, String> setHeaders,
                @NotNull @Valid Set<String> removeHeaders,
                @NotNull @Valid ForwardingApiCommand credentialForwarding) {
            public HeadersApiCommand {
                trustedHeaders = trustedHeaders == null ? null : Set.copyOf(trustedHeaders);
                setHeaders = setHeaders == null ? null : Map.copyOf(setHeaders);
                removeHeaders = removeHeaders == null ? null : Set.copyOf(removeHeaders);
            }
        }

        public record ForwardingApiCommand(
                @NotNull @Valid AuthorizationHeaderPolicyApiEnum authorizationHeader,
                @NotNull @Valid CookieForwardingModeApiEnum cookies,
                @NotNull @Valid Set<String> allowedCookieNames) {
            public ForwardingApiCommand {
                allowedCookieNames = allowedCookieNames == null ? null : Set.copyOf(allowedCookieNames);
            }
        }

        public record BundleApiCommand(
                @NotNull @NotBlank @Valid String bundleId,
            @NotNull Map<@NotBlank String, @NotNull Map<String, Object>> capabilities) {
        public BundleApiCommand {
            capabilities = com.cloud.gateway.api.facade.admin.definition.CapabilityCompositionApiCommand.copyCapabilities(capabilities);
        }
    }

        public record BindingApiCommand(
                @NotNull @NotBlank @Valid String bindingId,
                @NotNull @NotBlank @Valid String bundleId,
                @Valid String serviceId,
                @Valid String endpointId,
                @NotNull @Valid Set<String> tags,
                @Valid String appId,
                @Valid String verifiedClientId,
                @Valid SubjectTypeApiEnum subjectType) {
            public BindingApiCommand {
                tags = tags == null ? null : Set.copyOf(tags);
            }
        }

    }
}
