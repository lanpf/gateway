package com.cloud.gateway.interfaces.rest;

import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_SNAPSHOT;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.snapshot.IngressAccessSnapshotQueryFacade;
import com.cloud.gateway.api.facade.admin.snapshot.SnapshotApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Currently effective published snapshot; available in every deployment. */
@RestController
@RequiredArgsConstructor
public class GatewayAccessSnapshotQueryController {
    private final IngressAccessSnapshotQueryFacade queryFacade;

    @GetMapping(ADMIN_SNAPSHOT)
    public Result<SnapshotApiResponse> snapshot() {
        return queryFacade.get();
    }
}
