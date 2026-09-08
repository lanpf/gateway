package com.cloud.gateway.api.policy;

import com.cloud.framework.core.Request;
import com.cloud.gateway.api.enums.ClientAppStatusApiEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record SaveClientAppPolicyApiCommand(
        @NotBlank String expectedSourceRevision,
        @NotBlank String clientAppId,
        @NotNull ClientAppStatusApiEnum status,
        @NotBlank String platform,
        @NotNull Set<@NotBlank String> allowedBrowserOrigins
) implements Request {
    public SaveClientAppPolicyApiCommand {
        allowedBrowserOrigins = allowedBrowserOrigins == null
                ? null : Set.copyOf(allowedBrowserOrigins);
    }
}
