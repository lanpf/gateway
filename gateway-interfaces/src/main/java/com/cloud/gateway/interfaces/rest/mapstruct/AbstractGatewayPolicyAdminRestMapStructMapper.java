package com.cloud.gateway.interfaces.rest.mapstruct;

import com.cloud.gateway.api.facade.admin.definition.*;
import com.cloud.gateway.api.facade.admin.snapshot.*;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminRequest;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminRestMapper;
import java.util.Set;
import java.util.List;
import com.cloud.framework.core.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public abstract class AbstractGatewayPolicyAdminRestMapStructMapper implements GatewayPolicyAdminRestMapper {
    @Override public abstract SaveCapabilityPolicyConfigApiCommand toCommand(GatewayPolicyAdminRequest.SaveCapabilityPolicyConfig source);
    @Override
    @Mapping(target = "appId", source = "targetAppId")
    public abstract SaveAppApiCommand toCommand(GatewayPolicyAdminRequest.SaveAppAccess source);
    @Override public abstract SaveClientApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientAccess source);
    @Override public abstract SaveClientCredentialApiCommand toCommand(GatewayPolicyAdminRequest.SaveClientCredential source);
    @Override public abstract SaveEndpointApiCommand toCommand(GatewayPolicyAdminRequest.SaveEndpointAccess source);
    @Override public abstract SetAppStatusApiCommand toCommand(GatewayPolicyAdminRequest.SetAppStatus source);
    @Override public abstract SetClientStatusApiCommand toCommand(GatewayPolicyAdminRequest.SetClientStatus source);
    @Override public abstract SetEndpointStatusApiCommand toCommand(GatewayPolicyAdminRequest.SetEndpointStatus source);
    @Override public abstract RollbackSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.RollbackAccessSnapshot source);
    @Override public abstract ReleaseSnapshotApiCommand toCommand(GatewayPolicyAdminRequest.PublishAccessSnapshot source);

    protected abstract SaveClientCredentialApiCommand.CredentialApiCommand credential(GatewayPolicyAdminRequest.ClientCredential source);
    protected abstract SaveEndpointApiCommand.MatcherApiCommand matcher(GatewayPolicyAdminRequest.Matcher source);
    protected List<SaveEndpointApiCommand.MatcherApiCommand> matchers(List<GatewayPolicyAdminRequest.Matcher> source) {
        return source.stream().map(this::matcher).toList();
    }
    protected Set<String> strings(Set<String> source) { return Set.copyOf(source); }
}
