package com.cloud.gateway.domain.policy.capability;

/** Pure admission and observation rules; transport adapters supply facts, not transport objects. */
public final class DownstreamGovernanceRules {
    private static final String READ_METHOD = "GET";
    private static final String STREAMING_TAG = "streaming";
    private static final String VARY_WILDCARD = "*";
    public enum Observation { SUCCESS, UPSTREAM_FAILURE, IGNORED }

    public boolean streaming(java.util.Set<String> endpointTags) {
        return endpointTags.contains(STREAMING_TAG);
    }

    public boolean canReadCache(CachePolicy cache, CacheRequestFacts request) {
        return cache != null && request != null && request.authorized()
                && READ_METHOD.equals(request.method()) && !request.streaming()
                && !request.subjectPresent() && !request.credentialsPresent() && !request.bodyPresent()
                && !request.rangeRequested() && !request.conditional() && !request.cacheDirectivesPresent()
                && !request.bodyFramingPresent();
    }

    public boolean canFallback(CircuitBreakerPolicy.Fallback fallback, boolean classifiedFailure) {
        return fallback == CircuitBreakerPolicy.Fallback.STALE_CACHE && classifiedFailure;
    }

    public boolean canStore(CachePolicy cache, CacheRequestFacts request, CacheResponseFacts response) {
        return canReadCache(cache, request) && response.status() == 200
                && response.bodyBytes() <= cache.maxBodyBytes() && !response.setsCookie()
                && !response.partial() && !response.download()
                && !response.varyNames().contains(VARY_WILDCARD)
                && cache.varyHeaders().containsAll(response.varyNames())
                && response.freshnessSupported()
                && (response.contentKind() == CacheResponseFacts.ContentKind.JSON
                        || response.contentKind() == CacheResponseFacts.ContentKind.PLAIN_TEXT);
    }

    public boolean canCapture(CachePolicy cache, CacheRequestFacts request, int status) {
        return canReadCache(cache, request) && status == 200;
    }

    public boolean isOriginFailure(int status) {
        return status == 500 || status == 502 || status == 503 || status == 504;
    }

    public Observation observe(int status) {
        if (isOriginFailure(status)) {
            return Observation.UPSTREAM_FAILURE;
        }
        if (status >= 200 && status < 400) {
            return Observation.SUCCESS;
        }
        return Observation.IGNORED;
    }
}
