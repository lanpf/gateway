package com.cloud.gateway.interfaces.rest;

import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_SNAPSHOT_PUBLISH;
import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_SNAPSHOT_ROLLBACK;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.snapshot.IngressAccessSnapshotCommandFacade;
import com.cloud.gateway.api.facade.admin.snapshot.SnapshotApiResponse;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminRequest.PublishAccessSnapshot;
import com.cloud.gateway.interfaces.rest.GatewayPolicyAdminRequest.RollbackAccessSnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Explicit publication and recovery; absent in read-only deployments. */
@RestController
@RequiredArgsConstructor
@ConditionalOnBean(IngressAccessSnapshotCommandFacade.class)
public class GatewayAccessSnapshotCommandController {
    private final IngressAccessSnapshotCommandFacade facade;
    private final GatewayPolicyAdminRestMapper mapper;

    @PostMapping(ADMIN_SNAPSHOT_PUBLISH)
    public Result<SnapshotApiResponse> publish(@Valid @RequestBody PublishAccessSnapshot request) {
        return facade.release(mapper.toCommand(request));
    }

    @PostMapping(ADMIN_SNAPSHOT_ROLLBACK)
    public Result<SnapshotApiResponse> rollback(@Valid @RequestBody RollbackAccessSnapshot request) {
        return facade.rollback(mapper.toCommand(request));
    }
}
