package com.cloud.gateway.interfaces.facade.admin.snapshot;

import com.cloud.gateway.api.facade.admin.snapshot.*;
import com.cloud.gateway.application.configuration.publication.*;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;

public interface AccessSnapshotApiMapper {
    PublishAccessSnapshotCommand toCommand(ReleaseSnapshotApiCommand source);
    RollbackAccessSnapshotCommand toCommand(RollbackSnapshotApiCommand source);
    SnapshotUpdateApiOutput toOutput(IngressSnapshotUpdateOutput source);
    SnapshotApiView toView(IngressSnapshot source);
}
