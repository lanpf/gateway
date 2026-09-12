package com.cloud.gateway.application.governance;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Locale;

/** Original request evidence, captured before client removal or rewriting.
 * authorized means this request has completed current-snapshot entrance authorization.
 * streaming is supplied by the endpoint/transport adapter, never by a caller assertion.
 * bodyPresent includes unframed HTTP/2 DATA and is independent of endpoint streaming capability.
 */
public record CacheRequest(String routeId, String endpointId, String appId, String clientId,
        String method, String path, String query, Map<String, List<String>> headers,
        boolean authorized, boolean streaming, Map<String, List<String>> forwardedHeaders, boolean subjectPresent,
        boolean bodyPresent) {
    public CacheRequest(String routeId, String endpointId, String appId, String clientId,
            String method, String path, String query, Map<String, List<String>> headers,
            boolean authorized, boolean streaming, Map<String, List<String>> forwardedHeaders, boolean subjectPresent) {
        this(routeId, endpointId, appId, clientId, method, path, query, headers,
                authorized, streaming, forwardedHeaders, subjectPresent, false);
    }
    /** Convenience for callers without request transformation or an authenticated Subject. */
    public CacheRequest(String routeId, String endpointId, String appId, String clientId,
            String method, String path, String query, Map<String, List<String>> headers,
            boolean authorized, boolean streaming) {
        this(routeId, endpointId, appId, clientId, method, path, query, headers, authorized, streaming, headers, false, false);
    }

    public CacheRequest {
        Objects.requireNonNull(routeId);
        Objects.requireNonNull(endpointId);
        Objects.requireNonNull(method);
        Objects.requireNonNull(path);
        headers = normalize(headers);
        forwardedHeaders = normalize(forwardedHeaders);
    }

    private static Map<String, List<String>> normalize(Map<String, List<String>> headers) {
        TreeMap<String, List<String>> normalized = new TreeMap<>();
        Objects.requireNonNull(headers).forEach((name, values) -> {
            String key = name.toLowerCase(Locale.ROOT);
            normalized.merge(key, List.copyOf(values), (first, second) -> {
                java.util.ArrayList<String> merged = new java.util.ArrayList<>(first);
                merged.addAll(second);
                return List.copyOf(merged);
            });
        });
        return Map.copyOf(normalized);
    }

    @Override
    public String toString() {
        return "CacheRequest[request evidence redacted]";
    }
}
