package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.Request;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * Content-only upsert of one App; full replacement of platform and allowedOrigins. Status is not
 * part of this command: creation initializes it to ENABLED (content reaches runtime only after
 * publication), and an existing App's status is never changed by a save — use setAppStatus.
 * <p>expectedSourceRevision is an opaque CAS token: null/empty asserts the app-policies definition
 * does not exist yet (initial creation); otherwise it must equal the revision returned by the last
 * read or save of that definition.
 */
public record SaveAppApiCommand(
        String expectedSourceRevision,
        @NotBlank String appId,
        @NotBlank String platform,
        Set<@NotBlank String> allowedOrigins
) implements Request { }
