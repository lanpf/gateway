package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.capability.runtime.CapabilityContext;
import com.cloud.gateway.domain.capability.runtime.CapabilitySlot;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;

/** Supplies authorized facts to the capability use case and hands its output to downstream consumers. */
@RequiredArgsConstructor
public final class AdmissionStageCommandService {
    private final CapabilityExecutionCommandService capabilities;

    public CompletionStage<AdmissionStageOutput> admit(AdmissionStageCommand command) {
        IngressTask<AdmissionStageOutput> task = new IngressTask<>();
        try {
            AccessStageOutput.Allowed allowed = command.access();
            PreparationStageOutput.Prepared prepared = allowed.prepared();
            Endpoint endpoint = ((EndpointMatch.Unique) prepared.endpointMatch()).endpoint();
            CapabilityContext context = new CapabilityContext(CapabilitySlot.POST_AUTH, prepared.evidence(),
                    endpoint, allowed.decision().grantedContext());
            CapabilityExecutionCommand capabilityCommand = new CapabilityExecutionCommand(
                    prepared.snapshot().content().capabilities(), context, prepared.execution().executed());
            task.finish(task.track(capabilities.execute(capabilityCommand))
                    .thenApply(output -> new AdmissionStageOutput(allowed, output.capabilities(), output.execution())));
        } catch (RuntimeException failure) {
            task.cancel(true);
            return CompletableFuture.failedFuture(failure);
        }
        return task;
    }
}
