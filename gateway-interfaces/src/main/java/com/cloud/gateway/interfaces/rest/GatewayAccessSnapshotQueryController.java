package com.cloud.gateway.interfaces.rest;

import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_SNAPSHOT;

import com.cloud.framework.core.Result;
import com.cloud.gateway.api.facade.admin.snapshot.AccessSnapshotQueryFacade;
import com.cloud.gateway.api.facade.admin.snapshot.SnapshotApiView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Currently effective published snapshot; available in every deployment. */
@RequestMapping
@ResponseBody
@RequiredArgsConstructor
public class GatewayAccessSnapshotQueryController {
    private final AccessSnapshotQueryFacade queryFacade;

    @GetMapping(ADMIN_SNAPSHOT)
    public Result<SnapshotApiView> snapshot() {
        return queryFacade.get();
    }
}
