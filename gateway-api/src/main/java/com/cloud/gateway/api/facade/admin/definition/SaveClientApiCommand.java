package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.Request;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * Content-only upsert of one client's access settings; existing credentials are never touched by
 * this command. Status is not part of this command: creation initializes it to ENABLED, and an
 * existing client's status is never changed by a save — use setClientStatus.
 * <p>expectedSourceRevision is an opaque CAS token: null/empty asserts the client-policies definition
 * does not exist yet (initial creation); otherwise it must equal the revision returned by the last
 * read or save of that definition.
 */
public record SaveClientApiCommand(
        String expectedSourceRevision,
        @NotBlank String clientId,
        Set<@NotBlank String> allowedAppIds
) implements Request {
    public SaveClientApiCommand {
        allowedAppIds = allowedAppIds == null ? null : Set.copyOf(allowedAppIds);
    }
}
