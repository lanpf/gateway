package com.cloud.gateway.domain.ingress.access;


import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;

import java.util.Locale;
import java.util.Set;

/**
 * One-time capture of raw request facts; later stages consume this record and never re-collect.
 * Signature evidence material is not part of the capture: whether it is collected depends on the
 * matched endpoint policy and stays with access-stage verification.
 */
public record RequestEvidence(
        String method,
        String rawPath,
        String rawQuery,
        AppId declaredAppId,
        ClientId declaredClientId,
        String origin,
        String remoteIp
) {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    public RequestEvidence {
        method = Require.notBlank(method).trim().toUpperCase(Locale.ROOT);
        // rawPath and rawQuery are signed verbatim: never decoded, trimmed, sorted or rewritten.
        Require.notBlank(rawPath);
        origin = origin == null ? null : Require.notBlank(origin).trim();
        remoteIp = Require.notBlank(remoteIp).trim();
    }

    /** Non-safe methods drive Browser Session origin validation. */
    public boolean unsafe() {
        return !SAFE_METHODS.contains(method);
    }
}
