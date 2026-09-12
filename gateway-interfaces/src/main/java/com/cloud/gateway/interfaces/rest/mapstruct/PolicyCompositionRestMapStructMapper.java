package com.cloud.gateway.interfaces.rest.mapstruct;
import com.cloud.gateway.api.policy.SavePolicyCompositionApiCommand;
import com.cloud.gateway.interfaces.rest.*;
import org.mapstruct.Mapper;
@Mapper(config = PolicyAdminMapperConfig.class)
public interface PolicyCompositionRestMapStructMapper extends PolicyCompositionRestMapper {
    @Override SavePolicyCompositionApiCommand toCommand(GatewayPolicyAdminRequest.SavePolicyComposition source);
}
