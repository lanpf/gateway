package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.quota.RequestQuotaExecutor;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/** Owns authentication join, access evaluation and post-authentication quota ordering. */
@RequiredArgsConstructor
public final class IngressAdmissionCommandService {
    private final IngressAccessCommandService access;
    private final RequestQuotaExecutor quotas;

    public CompletionStage<IngressAdmissionOutput> admit(IngressAccessCommand command,
            ClientSignatureEvidence evidence, Supplier<CompletionStage<SubjectAuthenticationOutcome>> host,
            Supplier<CompletionStage<SubjectAuthenticationOutcome>> browser, Set<String> applied) {
        IngressTask<IngressAdmissionOutput> task = new IngressTask<>();
        try {
            SubjectAuthencationRequirement requirement = command.snapshot().content().endpointPolicies()
                    .get(command.endpoint().endpointId()).subjectRequirement();
            CompletionStage<ClientVerificationOutcome> client = task.track(
                    access.authenticateClient(command, evidence));
            if (task.isDone()) {
                return task;
            }
            CompletionStage<SubjectAuthenticationOutcome> subject = task.track(
                    access.authenticateSubject(requirement, host, browser));
            task.finish(client.thenCombine(subject, (clientIdentity, subjectIdentity) ->
                    access.evaluate(command, clientIdentity, subjectIdentity)).thenCompose(output -> {
                        if (output instanceof IngressAccessOutput.Denied) {
                            return CompletableFuture.completedFuture(new IngressAdmissionOutput(output, null));
                        }
                        IngressAccessOutput.Allowed allowed = (IngressAccessOutput.Allowed) output;
                        return task.track(quotas.acquire(allowed.policy().rateLimits(), allowed.dimensions(), false, applied))
                                .thenApply(decision -> new IngressAdmissionOutput(allowed, decision));
                    }));
        } catch (RuntimeException failure) {
            task.cancel(true);
            return CompletableFuture.failedFuture(failure);
        }
        return task;
    }
}
