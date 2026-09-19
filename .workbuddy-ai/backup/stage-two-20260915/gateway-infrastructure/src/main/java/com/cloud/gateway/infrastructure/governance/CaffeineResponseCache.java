package com.cloud.gateway.infrastructure.governance;

import com.cloud.gateway.application.governance.CacheRequest;
import com.cloud.gateway.application.governance.CachedRepresentation;
import com.cloud.gateway.application.governance.ResponseCache;
import com.cloud.gateway.domain.policy.capability.CachePolicy;
import com.cloud.gateway.domain.policy.capability.CircuitBreakerPolicy;
import com.cloud.gateway.domain.policy.capability.CacheRequestFacts;
import com.cloud.gateway.domain.policy.capability.CacheResponseFacts;
import com.cloud.gateway.domain.policy.capability.DownstreamGovernanceRules;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;

/** Conservative shared GET cache. All original header values participate in an irreversible key;
 * supported Vary is additionally restricted by policy. No background refresh or sliding expiration.
 * A bounded representation must be collected by the transport adapter before store is invoked.
 */
public final class CaffeineResponseCache implements ResponseCache {
    private static final int ENTRY_OVERHEAD_BYTES = 512;
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final Set<String> RETAINED_HEADERS = Set.of(HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CONTENT_ENCODING, HttpHeaders.CONTENT_LANGUAGE, HttpHeaders.CACHE_CONTROL,
            HttpHeaders.ETAG, HttpHeaders.LAST_MODIFIED, HttpHeaders.DATE, HttpHeaders.VARY,
            HttpHeaders.CONTENT_LOCATION);
    private static final Set<String> REJECTED_DIRECTIVES = Set.of("private", "no-store", "no-cache",
            "must-revalidate", "proxy-revalidate", "must-understand");
    private static final String MAX_AGE = "max-age";
    private static final String SHARED_MAX_AGE = "s-maxage";
    private static final String SHA_256 = "SHA-256";
    private static final String CACHE_ENTRIES_METRIC = "gateway.governance.cache.entries";
    private static final String CACHE_WEIGHT_METRIC = "gateway.governance.cache.weight";
    private final Cache<String, Entry> entries;
    private final Clock clock;
    private final long maximumWeightBytes;
    private final GovernanceMetrics metrics;
    private final DownstreamGovernanceRules rules = new DownstreamGovernanceRules();

    public CaffeineResponseCache(long maximumWeightBytes, Clock clock) {
        this(maximumWeightBytes, clock, null);
    }

    public CaffeineResponseCache(long maximumWeightBytes, Clock clock, MeterRegistry registry) {
        Assert.isTrue(maximumWeightBytes > ENTRY_OVERHEAD_BYTES, "cache weight must exceed entry overhead");
        this.clock = Objects.requireNonNull(clock);
        this.maximumWeightBytes = maximumWeightBytes;
        this.metrics = new GovernanceMetrics(registry);
        entries = Caffeine.newBuilder().maximumWeight(maximumWeightBytes)
                .weigher((String key, Entry value) -> value.weight())
                .expireAfterWrite(Duration.ofDays(2)).build();
        if (registry != null) {
            Gauge.builder(CACHE_ENTRIES_METRIC, entries, Cache::estimatedSize).register(registry);
            Gauge.builder(CACHE_WEIGHT_METRIC, entries,
                    cache -> cache.policy().eviction().orElseThrow().weightedSize().orElse(0)).register(registry);
        }
    }

    @Override
    public Optional<CachedRepresentation> lookup(CacheRequest request, CachePolicy policy) {
        return find(request, policy, false);
    }

    @Override
    public Optional<CachedRepresentation> fallback(CacheRequest request, CachePolicy policy,
            CircuitBreakerPolicy.Fallback fallback, UpstreamFailure failure) {
        if (!rules.canFallback(fallback, failure != null)) {
            return Optional.empty();
        }
        return find(request, policy, true);
    }

    private Optional<CachedRepresentation> find(CacheRequest request, CachePolicy policy, boolean staleAllowed) {
        if (!eligible(request, policy)) {
            metrics.lookup(GovernanceMetrics.Lookup.BYPASS);
            return Optional.empty();
        }
        String key = key(request, policy);
        Entry entry = entries.getIfPresent(key);
        if (entry == null) {
            metrics.lookup(staleAllowed ? GovernanceMetrics.Lookup.STALE_MISS : GovernanceMetrics.Lookup.MISS);
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (!now.isBefore(entry.staleUntil())) {
            entries.asMap().remove(key, entry);
            metrics.lookup(staleAllowed ? GovernanceMetrics.Lookup.STALE_MISS : GovernanceMetrics.Lookup.MISS);
            return Optional.empty();
        }
        if (!staleAllowed && !now.isBefore(entry.freshUntil())) {
            metrics.lookup(GovernanceMetrics.Lookup.MISS);
            return Optional.empty();
        }
        TreeMap<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(entry.representation().headers());
        long residence = Math.max(0, Duration.between(entry.storedAt(), now).toSeconds());
        headers.put(HttpHeaders.AGE, List.of(Long.toString(entry.initialAgeSeconds() + residence)));
        metrics.lookup(staleAllowed ? GovernanceMetrics.Lookup.STALE_HIT : GovernanceMetrics.Lookup.FRESH_HIT);
        return Optional.of(new CachedRepresentation(entry.representation().status(), headers,
                entry.representation().body()));
    }

    @Override
    public boolean store(CacheRequest request, CachePolicy policy, CachedRepresentation representation,
            Duration responseDelay) {
        boolean stored = storeEligible(request, policy, representation, responseDelay);
        metrics.store(stored);
        return stored;
    }

    private boolean storeEligible(CacheRequest request, CachePolicy policy, CachedRepresentation representation,
            Duration responseDelay) {
        Objects.requireNonNull(representation);
        Objects.requireNonNull(responseDelay);
        Assert.isTrue(!responseDelay.isNegative(), "responseDelay must be non-negative");
        if (!eligible(request, policy)) {
            return false;
        }
        String key = key(request, policy);
        HttpHeaders headers = headers(representation.headers());
        Optional<Freshness> freshLifetime = lifetime(headers, policy);
        CacheResponseFacts facts = new CacheResponseFacts(representation.status(), representation.bodySize(),
                contentKind(headers), representation.cookiesPresent() || headers.containsKey(HttpHeaders.SET_COOKIE),
                headers.containsKey(HttpHeaders.CONTENT_RANGE), headers.containsKey(HttpHeaders.CONTENT_DISPOSITION),
                varyNames(headers), freshLifetime.isPresent());
        if (!rules.canStore(policy, requestFacts(request), facts) || headerBytes(headers) > MAX_HEADER_BYTES) {
            entries.invalidate(key);
            return false;
        }
        Instant now = clock.instant();
        long initialAge;
        try {
            String age = headers.getFirst(HttpHeaders.AGE);
            initialAge = age == null ? 0 : Long.parseLong(age);
            if (initialAge < 0 || initialAge > Duration.ofDays(365).toSeconds()) {
                entries.invalidate(key);
                return false;
            }
            if (responseDelay.compareTo(Duration.ofDays(2)) > 0) {
                entries.invalidate(key);
                return false;
            }
            // Round up to avoid extending freshness by fractional transport seconds.
            initialAge += responseDelay.getSeconds() + (responseDelay.getNano() > 0 ? 1 : 0);
            long date = headers.getDate();
            if (date >= 0) {
                initialAge = Math.max(initialAge,
                        Math.max(0, Duration.between(Instant.ofEpochMilli(date), now).toSeconds()));
            }
        } catch (IllegalArgumentException exception) {
            entries.invalidate(key);
            return false;
        }
        Freshness freshness = freshLifetime.orElseThrow();
        Duration remaining = freshness.lifetime().minusSeconds(initialAge);
        if (remaining.isNegative() || remaining.isZero()) {
            entries.invalidate(key);
            return false;
        }
        TreeMap<String, List<String>> retained = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        RETAINED_HEADERS.forEach(name -> {
            if (headers.containsKey(name)) {
                retained.put(name, List.copyOf(headers.get(name)));
            }
        });
        CachedRepresentation payload = new CachedRepresentation(200, retained, representation.body());
        Instant freshUntil = now.plus(remaining);
        int weight = ENTRY_OVERHEAD_BYTES + headerBytes(headers) + payload.bodySize();
        if (weight > maximumWeightBytes) {
            entries.invalidate(key);
            return false;
        }
        Instant staleUntil = freshness.staleAllowed() ? freshUntil.plus(policy.staleWindow()) : freshUntil;
        entries.put(key, new Entry(payload, now, freshUntil, staleUntil, initialAge, weight));
        entries.cleanUp();
        return true;
    }

    private boolean eligible(CacheRequest request, CachePolicy policy) {
        return request != null && rules.canReadCache(policy, requestFacts(request));
    }

    @Override
    public CacheRequestFacts requestFacts(CacheRequest request) {
        HttpHeaders headers = headers(request.headers());
        boolean credentials = headers.containsKey(HttpHeaders.AUTHORIZATION) || headers.containsKey(HttpHeaders.COOKIE);
        boolean conditional = headers.containsKey(HttpHeaders.IF_RANGE)
                || headers.containsKey(HttpHeaders.IF_MATCH)
                || headers.containsKey(HttpHeaders.IF_NONE_MATCH)
                || headers.containsKey(HttpHeaders.IF_MODIFIED_SINCE)
                || headers.containsKey(HttpHeaders.IF_UNMODIFIED_SINCE);
        return new CacheRequestFacts(request.method(), request.authorized(), request.streaming(),
                request.subjectPresent(), credentials, request.bodyPresent(),
                headers.containsKey(HttpHeaders.RANGE), conditional,
                headers.containsKey(HttpHeaders.CACHE_CONTROL) || headers.containsKey(HttpHeaders.PRAGMA),
                headers.containsKey(HttpHeaders.TRANSFER_ENCODING) || headers.containsKey(HttpHeaders.CONTENT_LENGTH));
    }

    private static CacheResponseFacts.ContentKind contentKind(HttpHeaders headers) {
        try {
            MediaType type = headers.getContentType();
            if (type != null && !type.isWildcardType() && !type.isWildcardSubtype()) {
                if (MediaType.APPLICATION_JSON.isCompatibleWith(type)) {
                    return CacheResponseFacts.ContentKind.JSON;
                }
                if (MediaType.TEXT_PLAIN.isCompatibleWith(type)) {
                    return CacheResponseFacts.ContentKind.PLAIN_TEXT;
                }
            }
            return CacheResponseFacts.ContentKind.OTHER;
        } catch (IllegalArgumentException exception) {
            return CacheResponseFacts.ContentKind.OTHER;
        }
    }

    private static Set<String> varyNames(HttpHeaders headers) {
        return headers.getVary().stream().map(name -> name.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Optional<Freshness> lifetime(HttpHeaders headers, CachePolicy policy) {
        TreeMap<String, String> directives = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String line : headers.getOrEmpty(HttpHeaders.CACHE_CONTROL)) {
            for (String token : line.split(",")) {
                String[] pair = token.trim().split("=", 2);
                String name = pair[0].trim().toLowerCase(java.util.Locale.ROOT);
                if (REJECTED_DIRECTIVES.contains(name) || directives.containsKey(name)) {
                    return Optional.empty();
                }
                directives.put(name, pair.length == 2 ? pair[1].trim().replace("\"", "") : "");
            }
        }
        Duration limit = policy.ttl();
        String seconds = directives.getOrDefault(SHARED_MAX_AGE, directives.get(MAX_AGE));
        if (seconds != null) {
            try {
                long value = Long.parseLong(seconds);
                if (value <= 0) {
                    return Optional.empty();
                }
                Duration originLimit = Duration.ofSeconds(value);
                if (originLimit.compareTo(limit) < 0) {
                    limit = originLimit;
                }
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        } else if (headers.containsKey(HttpHeaders.EXPIRES)) {
            // No heuristic freshness or legacy Expires interpretation in v1.
            return Optional.empty();
        }
        // s-maxage incorporates proxy-revalidate: do not serve it stale without revalidation.
        return Optional.of(new Freshness(limit, !directives.containsKey(SHARED_MAX_AGE)));
    }

    private static HttpHeaders headers(Map<String, List<String>> source) {
        HttpHeaders result = new HttpHeaders();
        source.forEach(result::addAll);
        return result;
    }

    private static int headerBytes(HttpHeaders headers) {
        long size = 0;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            size += (long) entry.getKey().length() * Character.BYTES;
            for (String value : entry.getValue()) {
                size += (long) value.length() * Character.BYTES + 64;
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, size);
    }

    private static String key(CacheRequest request, CachePolicy policy) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            add(digest, request.routeId());
            add(digest, request.endpointId());
            add(digest, request.appId());
            add(digest, request.clientId());
            add(digest, request.method());
            add(digest, request.path());
            add(digest, request.query());
            add(digest, policy.ttl().toString());
            add(digest, policy.staleWindow().toString());
            add(digest, Integer.toString(policy.maxBodyBytes()));
            add(digest, Integer.toString(policy.varyHeaders().size()));
            policy.varyHeaders().stream().sorted().forEach(name -> add(digest, name));
            // Conservative isolation even if an origin forgets Vary. No raw evidence retained in keys.
            addHeaders(digest, request.headers());
            // Separate length-delimited controlled section; a caller cannot forge this via original header names.
            addHeaders(digest, request.forwardedHeaders());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 implementation unavailable", exception);
        }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value == null ? -1 : bytes.length).array());
        digest.update(bytes);
    }

    private static void addHeaders(MessageDigest digest, Map<String, List<String>> headers) {
        add(digest, Integer.toString(headers.size()));
        new TreeMap<>(headers).forEach((name, values) -> {
            add(digest, name);
            add(digest, Integer.toString(values.size()));
            values.forEach(value -> add(digest, value));
        });
    }

    private record Entry(CachedRepresentation representation, Instant storedAt, Instant freshUntil,
            Instant staleUntil, long initialAgeSeconds, int weight) { }

    private record Freshness(Duration lifetime, boolean staleAllowed) { }
}
