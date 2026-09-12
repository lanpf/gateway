package com.cloud.gateway.domain.policy.capability;

import java.util.Set;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;

/** Parsed representation facts; wire-format parsing belongs to the adapter. */
public record CacheResponseFacts(int status, int bodyBytes, ContentKind contentKind,
        boolean setsCookie, boolean partial, boolean download, Set<String> varyNames,
        boolean freshnessSupported) {
    public CacheResponseFacts {
        Require.notNull(varyNames, DomainException::missingField);
        varyNames.forEach(name -> Require.notNull(name, DomainException::missingField));
        varyNames = Set.copyOf(varyNames);
    }
    public enum ContentKind { JSON, PLAIN_TEXT, OTHER }
}
