package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.framework.core.RequestHeader;

import com.cloud.gateway.api.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.*;

/** Typed v1 policy configuration payload; no domain types cross the API boundary. */
public record CapabilityPolicyConfigApiCommand(
        @NotNull Map<@NotBlank String, @Valid BundleApiCommand> bundles,
        @NotNull Map<@NotBlank String, @NotNull @Valid BindingApiCommand> bindings) {
    public CapabilityPolicyConfigApiCommand {
        bundles = bundles == null ? null : NullSafe.copyOf(bundles);
        bindings = bindings == null ? null : NullSafe.copyOf(bindings);
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
            rules = rules == null ? null : NullSafe.copyOf(rules);
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
            trustedHeaders = trustedHeaders == null ? null : NullSafe.copyOf(trustedHeaders);
            setHeaders = setHeaders == null ? null : NullSafe.copyOf(setHeaders);
            removeHeaders = removeHeaders == null ? null : NullSafe.copyOf(removeHeaders);
        }
    }

    public record ForwardingApiCommand(
            @NotNull @Valid AuthorizationHeaderPolicyApiEnum authorizationHeader,
            @NotNull @Valid CookieForwardingModeApiEnum cookies,
            @NotNull @Valid Set<String> allowedCookieNames) {
        public ForwardingApiCommand {
            allowedCookieNames = allowedCookieNames == null ? null : NullSafe.copyOf(allowedCookieNames);
        }
    }

    public record BundleApiCommand(
            @NotNull @NotBlank @Valid String bundleId,
            @NotNull Map<@NotBlank String, @NotNull Map<String, Object>> capabilities) {
        public BundleApiCommand {
            capabilities = com.cloud.gateway.api.facade.admin.definition.CapabilityPolicyConfigApiCommand.copyCapabilities(capabilities);
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
            tags = tags == null ? null : NullSafe.copyOf(tags);
        }
    }
    static Map<String, Map<String, Object>> copyCapabilities(Map<String, Map<String, Object>> source) {
        if (source == null) { return null; }
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        NullSafe.forEach(source, (id, node) -> copy.put(id, copyObject(node)));
        return Collections.unmodifiableMap(copy);
    }
    private static Map<String, Object> copyObject(Map<?, ?> source) {
        Objects.requireNonNull(source, "configuration");
        NullSafe.forEach(source.keySet());
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String name)) { throw new IllegalArgumentException("JSON object keys must be strings"); }
            copy.put(name, copyValue(value));
        });
        return Collections.unmodifiableMap(copy);
    }
    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> object) { return copyObject(object); }
        if (value instanceof List<?> array) { return Collections.unmodifiableList(array.stream().map(CapabilityPolicyConfigApiCommand::copyValue).toList()); }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Double || value instanceof Float
                || value instanceof Short || value instanceof Byte || value instanceof java.math.BigDecimal
                || value instanceof java.math.BigInteger) { return value; }
        throw new IllegalArgumentException("Capability configuration must contain only JSON values");
    }
}
