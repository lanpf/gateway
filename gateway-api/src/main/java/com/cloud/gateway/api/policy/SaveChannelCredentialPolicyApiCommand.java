package com.cloud.gateway.api.policy;

import com.cloud.framework.core.Request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveChannelCredentialPolicyApiCommand(
        @NotBlank String expectedSourceRevision,
        @NotBlank String channelCode,
        @NotNull @Valid ChannelCredentialApiCommand activeCredential,
        @Valid ChannelCredentialApiCommand graceCredential
) implements Request { }
