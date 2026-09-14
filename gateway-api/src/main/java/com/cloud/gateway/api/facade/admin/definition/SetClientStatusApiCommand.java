package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.ClientStatusApiEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Dedicated enable/disable transition of one client; the only command that changes its status.
 * Content edits by others conflict through the same definition-level CAS token.
 */
public record SetClientStatusApiCommand(
        String expectedSourceRevision,
        @NotBlank String clientId,
        @NotNull ClientStatusApiEnum status
) implements Request { }
