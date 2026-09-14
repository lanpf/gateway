package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.EndpointStatusApiEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Dedicated enable/disable transition of one endpoint; the only command that changes its status.
 * Content edits by others conflict through the same definition-level CAS token.
 */
public record SetEndpointStatusApiCommand(
        String expectedSourceRevision,
        @NotBlank String endpointId,
        @NotNull EndpointStatusApiEnum status
) implements Request { }
