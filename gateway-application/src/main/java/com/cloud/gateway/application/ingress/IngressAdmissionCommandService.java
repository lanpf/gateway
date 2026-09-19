package com.cloud.gateway.application.ingress;

import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationOutcome;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/** Composes access and admission after preparation, owning short-circuiting and cancellation. */
@RequiredArgsConstructor
public final class IngressAdmissionCommandService {
    private final AccessStageCommandService access;
    private final AdmissionStageCommandService admission;

    public CompletionStage<IngressAdmissionOutput> admit(AccessStageCommand command,
            Supplier<CompletionStage<SubjectAuthenticationOutcome>> subjectAuthentication) {
        IngressTask<IngressAdmissionOutput> task = new IngressTask<>();
        try {
            task.finish(task.track(access.evaluate(command, subjectAuthentication)).thenCompose(output -> {
                if (output instanceof AccessStageOutput.Denied denied) {
                    return CompletableFuture.completedFuture(new IngressAdmissionOutput.AccessDenied(denied));
                }
                if (task.isDone()) {
                    return CompletableFuture.failedFuture(new CancellationException());
                }
                AccessStageOutput.Allowed allowed = (AccessStageOutput.Allowed) output;
                return task.track(admission.admit(new AdmissionStageCommand(allowed)))
                        .thenApply(IngressAdmissionOutput.Evaluated::new);
            }));
        } catch (RuntimeException failure) {
            task.cancel(true);
            return CompletableFuture.failedFuture(failure);
        }
        return task;
    }
}
