package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.AppStatusApiEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Dedicated enable/disable transition of one App; the only command that changes its status.
 * Content edits by others conflict through the same definition-level CAS token.
 */
public record SetAppStatusApiCommand(
        String expectedSourceRevision,
        @NotBlank String appId,
        @NotNull AppStatusApiEnum status
) implements Request { }
