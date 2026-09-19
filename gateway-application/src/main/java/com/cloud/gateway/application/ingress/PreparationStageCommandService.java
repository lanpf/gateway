package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.policy.execution.CapabilityContext;
import com.cloud.gateway.domain.policy.execution.CapabilitySlot;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;

/**
 * Stage one of ingress admission: capture the snapshot once, classify matched candidates and
 * execute the pre-authentication capability slot. Ambiguity and unknown paths are not rejected here.
 */
@RequiredArgsConstructor
public final class PreparationStageCommandService {
    private final AccessSnapshotCapturer capturer;
    private final EndpointMatchSource matches;
    private final CapabilityExecutionCommandService capabilities;
    private final PreparationObserver observer;

    public CompletionStage<PreparationStageOutput> prepare(PreparationStageCommand command) {
        RequestEvidence evidence = command.evidence();
        IngressSnapshot snapshot = capturer.capture().snapshot();
        List<Endpoint> candidates = matches.match(
                snapshot.content().endpointRegistry(), evidence.method(), evidence.rawPath());
        EndpointMatch match = EndpointMatch.of(candidates);
        if (match instanceof EndpointMatch.Ambiguous) {
            observer.ambiguousMatch();
        }
        Endpoint endpoint = match instanceof EndpointMatch.Unique unique ? unique.endpoint() : null;

        CapabilityContext context = new CapabilityContext(CapabilitySlot.PRE_AUTH, evidence, endpoint, null);
        CapabilityExecutionCommand capabilityCommand = new CapabilityExecutionCommand(
                snapshot.content().capabilityComposition(), context, Set.of());
        IngressTask<PreparationStageOutput> task = new IngressTask<>();
        task.finish(task.track(capabilities.execute(capabilityCommand))
                .thenApply(output -> output.execution().allowed()
                        ? new PreparationStageOutput.Prepared(snapshot, match, evidence, output.execution())
                        : new PreparationStageOutput.Denied(output.execution())));
        return task;
    }
}
