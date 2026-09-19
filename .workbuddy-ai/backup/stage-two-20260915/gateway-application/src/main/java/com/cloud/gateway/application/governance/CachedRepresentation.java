package com.cloud.gateway.application.governance;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Buffered representation only; the transport adapter must bound collection before construction. */
public record CachedRepresentation(int status, Map<String, List<String>> headers, byte[] body, boolean cookiesPresent) {
    public CachedRepresentation(int status, Map<String, List<String>> headers, byte[] body) {
        this(status, headers, body, false);
    }

    public CachedRepresentation {
        headers = Objects.requireNonNull(headers).entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        body = Objects.requireNonNull(body).clone();
    }

    @Override
    public byte[] body() { return body.clone(); }

    public int bodySize() { return body.length; }

    @Override
    public String toString() {
        return "CachedRepresentation[status=" + status + ", bodyBytes=" + body.length + "]";
    }
}
