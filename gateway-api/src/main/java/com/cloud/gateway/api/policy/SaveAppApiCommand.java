package com.cloud.gateway.api.policy;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.AppStatusApiEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record SaveAppApiCommand(
        @NotBlank String expectedSourceRevision,
        @NotBlank String appId,
        @NotBlank String platform,
        @NotNull AppStatusApiEnum status,
        Set<@NotBlank String> allowedOrigins
) implements Request {
    public SaveAppApiCommand {
        allowedOrigins = allowedOrigins == null ? null : Set.copyOf(allowedOrigins);
    }
}
