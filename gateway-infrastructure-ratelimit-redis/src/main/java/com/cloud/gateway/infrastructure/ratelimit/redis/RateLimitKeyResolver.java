package com.cloud.gateway.infrastructure.ratelimit.redis;

import com.cloud.framework.core.naming.ResourceNameResolver;
import com.cloud.framework.starter.autoconfigure.naming.AbstractKeyResolver;
import com.cloud.gateway.domain.policy.capability.RateLimitRule;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.util.Assert;

/** Owns business segments only; injected resolver owns application namespace. */
public final class RateLimitKeyResolver extends AbstractKeyResolver {
    private static final String PREFIX = "ratelimit";

    public RateLimitKeyResolver(ResourceNameResolver resourceNameResolver) {
        super(resourceNameResolver);
    }

    @Override
    protected String[] prefixes() {
        return new String[]{PREFIX};
    }

    String bucket(RateLimitRule rule, String value) {
        Assert.hasText(value, "dimension value must not be blank");
        return resolve(encode(rule.ruleId()), rule.dimension().name(), encode(value));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
