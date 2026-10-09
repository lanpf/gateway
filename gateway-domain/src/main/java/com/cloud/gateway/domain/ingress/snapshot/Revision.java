package com.cloud.gateway.domain.ingress.snapshot;


import com.cloud.framework.core.validation.Require;

public record Revision(String value) {
    public Revision {
        value = Require.notBlank(value).trim();
    }
}
