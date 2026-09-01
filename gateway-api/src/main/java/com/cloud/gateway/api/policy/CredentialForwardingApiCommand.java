package com.cloud.gateway.api.policy;

import com.cloud.gateway.api.enums.AuthorizationHeaderPolicyApiEnum;
import com.cloud.gateway.api.enums.CookieForwardingModeApiEnum;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record CredentialForwardingApiCommand(
        @NotNull AuthorizationHeaderPolicyApiEnum authorizationHeader,
        @NotNull CookieForwardingModeApiEnum cookies,
        @NotNull Set<String> allowedCookieNames
) {
    public CredentialForwardingApiCommand {
        allowedCookieNames = allowedCookieNames == null ? null : Set.copyOf(allowedCookieNames);
    }
}
