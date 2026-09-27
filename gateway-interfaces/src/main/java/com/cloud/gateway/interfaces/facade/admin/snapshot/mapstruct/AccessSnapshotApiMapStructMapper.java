package com.cloud.gateway.interfaces.facade.admin.snapshot.mapstruct;

import com.cloud.gateway.api.facade.admin.snapshot.*;
import com.cloud.gateway.application.configuration.publication.*;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion;
import com.cloud.gateway.interfaces.facade.admin.snapshot.AccessSnapshotApiMapper;
import com.cloud.framework.core.mapper.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public abstract class AccessSnapshotApiMapStructMapper implements AccessSnapshotApiMapper {
    @Override public abstract PublishAccessSnapshotCommand toCommand(ReleaseSnapshotApiCommand source);
    @Override public abstract RollbackAccessSnapshotCommand toCommand(RollbackSnapshotApiCommand source);
    @Override public abstract SnapshotUpdateApiOutput toOutput(IngressSnapshotUpdateOutput source);
    @Override
    @Mapping(target = "snapshotVersion", source = "version")
    public abstract SnapshotApiView toView(IngressSnapshot source);
    protected SnapshotVersion version(long source) { return new SnapshotVersion(source); }
    protected long versionValue(SnapshotVersion source) { return source.value(); }
}
