package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.app.AppStatus;

/** Dedicated enable/disable transition of one App; the only command that changes its status. */
public record SetAppStatusCommand(Revision expectedSourceRevision, AppId appId, AppStatus status) { }
