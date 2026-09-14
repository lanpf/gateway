package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.quota.RequestQuotaExecutor;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.policy.capability.RateLimitRuleId;
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
            ClientSignatureEvidence evidence, Supplier<CompletionStage<SubjectAuthenticationOutcome>> subjectAuth,
            Set<RateLimitRuleId> applied) {
        IngressTask<IngressAdmissionOutput> task = new IngressTask<>();
        try {
            if (!(command.endpointMatch() instanceof EndpointMatch.Unique unique)) {
                // Ambiguity and unknown paths deny uniformly; no authentication is joined for them.
                task.finish(CompletableFuture.completedFuture(new IngressAdmissionOutput(
                        access.evaluate(command, ClientVerificationOutcome.absent(),
                                SubjectAuthenticationOutcome.absent()), null)));
                return task;
            }
            CompletionStage<ClientVerificationOutcome> client = task.track(
                    access.authenticateClient(command, evidence));
            if (task.isDone()) {
                return task;
            }
            CompletionStage<SubjectAuthenticationOutcome> subject = task.track(subjectAuth.get());
            task.finish(client.thenCombine(subject, (clientIdentity, subjectIdentity) ->
                    admitOutput(command, clientIdentity, subjectIdentity)).thenCompose(output -> {
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

    private IngressAccessOutput admitOutput(IngressAccessCommand command,
            ClientVerificationOutcome clientVerification, SubjectAuthenticationOutcome subjectAuthentication) {
        return access.evaluate(command, clientVerification, subjectAuthentication);
    }
}
