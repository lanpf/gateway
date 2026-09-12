package com.cloud.gateway.api.policy;
import com.cloud.framework.core.Request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record SavePolicyCompositionApiCommand(@NotBlank String expectedSourceRevision,
        @NotNull @Valid PolicyCompositionApiCommand policies) implements Request { }
