package com.cloud.gateway.api.policy;

import com.cloud.framework.core.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record SaveChannelAccessPolicyApiCommand(
        @NotBlank String expectedSourceRevision,
        @NotBlank String channelCode,
        @NotNull Set<@NotBlank String> allowedClientAppIds
) implements Request {
    public SaveChannelAccessPolicyApiCommand {
        allowedClientAppIds = allowedClientAppIds == null ? null : Set.copyOf(allowedClientAppIds);
    }
}
