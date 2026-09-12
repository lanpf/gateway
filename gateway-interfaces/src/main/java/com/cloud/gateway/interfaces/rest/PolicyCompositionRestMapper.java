package com.cloud.gateway.interfaces.rest;
import com.cloud.gateway.api.policy.SavePolicyCompositionApiCommand;
public interface PolicyCompositionRestMapper {
    SavePolicyCompositionApiCommand toCommand(GatewayPolicyAdminRequest.SavePolicyComposition source);
}
