package com.cloud.gateway.domain.ingress.access.subject;


import com.cloud.framework.core.validation.Require;

public record SubjectId(String value) {
    public SubjectId {
        value = Require.notBlank(value).trim();
    }
}
