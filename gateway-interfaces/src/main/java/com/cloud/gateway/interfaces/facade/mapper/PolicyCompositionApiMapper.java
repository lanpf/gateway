package com.cloud.gateway.interfaces.facade.mapper;

import com.cloud.gateway.api.policy.SavePolicyCompositionApiCommand;
import com.cloud.gateway.application.policy.SavePolicyCompositionCommand;

public interface PolicyCompositionApiMapper {
    SavePolicyCompositionCommand toCommand(SavePolicyCompositionApiCommand source);
}
