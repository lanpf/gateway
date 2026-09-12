package com.cloud.gateway.application.governance;

/** Complete admission outcome: the protocol adapter does not order cache and circuit calls. */
public sealed interface DownstreamAdmissionOutput {
    record CacheHit(CachedRepresentation representation) implements DownstreamAdmissionOutput { }
    record Execute(DownstreamExecution execution) implements DownstreamAdmissionOutput { }
    record Fallback(DownstreamExecution execution, ResponseCache.UpstreamFailure failure)
            implements DownstreamAdmissionOutput { }
}
