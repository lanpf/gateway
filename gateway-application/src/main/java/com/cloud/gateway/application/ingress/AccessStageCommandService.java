package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.domain.ingress.access.AccessDecision;
import com.cloud.gateway.domain.ingress.access.AccessDenyReason;
import com.cloud.gateway.domain.ingress.access.AccessEvaluator;
import com.cloud.gateway.domain.ingress.access.AccessRequest;
import com.cloud.gateway.domain.ingress.access.RequestEvidence;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/**
 * HTTP-neutral access stage: owns client verification, the subject-authentication join, the access
 * decision. Capability binding resolution belongs to the admission stage.
 * Named for the stage because {@code AccessCommandService} would
 * read as one of the {@code *AccessCommandService} definition-editing services. The sibling stages
 * {@code PreparationStageCommandService} and {@code AdmissionStageCommandService} carry the same
 * qualifier so the three pipeline stages stay recognisable as one family.
 */
@RequiredArgsConstructor
public final class AccessStageCommandService {
    private final ClientCredentialAuthenticator clientAuthenticator;
    private final AccessEvaluator evaluator;
    private final Clock clock;

    /**
     * The single entry point of this stage. The subject authenticator stays a supplier so that
     * ambiguity and unknown paths deny without ever triggering authentication.
     */
    public CompletionStage<AccessStageOutput> evaluate(AccessStageCommand command,
            Supplier<CompletionStage<SubjectAuthenticationOutcome>> subjectAuthentication) {
        IngressTask<AccessStageOutput> task = new IngressTask<>();
        try {
            PreparationStageOutput.Prepared prepared = command.prepared();
            if (!(prepared.endpointMatch() instanceof EndpointMatch.Unique unique)) {
                // Ambiguity and unknown paths deny uniformly; no authentication is joined for them.
                task.finish(CompletableFuture.completedFuture(
                        denied(prepared, AccessDenyReason.ENDPOINT_NOT_FOUND)));
                return task;
            }
            if (prepared.evidence().declaredAppId() == null) {
                task.finish(CompletableFuture.completedFuture(
                        denied(prepared, AccessDenyReason.APP_NOT_FOUND)));
                return task;
            }
            CompletionStage<ClientVerificationOutcome> client = task.track(authenticateClient(command));
            if (task.isDone()) {
                return task;
            }
            CompletionStage<SubjectAuthenticationOutcome> subject = task.track(subjectAuthentication.get());
            task.finish(client.thenCombine(subject, (clientIdentity, subjectIdentity) ->
                    decide(prepared, unique, clientIdentity, subjectIdentity)));
        } catch (RuntimeException failure) {
            task.cancel(true);
            return CompletableFuture.failedFuture(failure);
        }
        return task;
    }

    /** Verification runs only for a declared client with configured credentials and submitted evidence. */
    private CompletionStage<ClientVerificationOutcome> authenticateClient(AccessStageCommand command) {
        PreparationStageOutput.Prepared prepared = command.prepared();
        RequestEvidence evidence = prepared.evidence();
        ClientSignatureEvidence signatureEvidence = command.signatureEvidence();
        if (evidence.declaredClientId() == null) {
            return CompletableFuture.completedFuture(ClientVerificationOutcome.absent());
        }
        Client client = prepared.snapshot().content().clientRegistry()
                .find(evidence.declaredClientId()).orElse(null);
        Credentials credentials = client == null ? null : client.access().credentials();
        if (credentials == null || signatureEvidence == null) {
            return CompletableFuture.completedFuture(
                    credentials == null && signatureEvidence != null
                            ? ClientVerificationOutcome.rejected()
                            : ClientVerificationOutcome.absent());
        }
        try {
            return clientAuthenticator.authenticate(credentials, evidence.declaredClientId(),
                    signatureEvidence, clock.instant());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(ClientVerificationOutcome.rejected());
        }
    }

    private AccessStageOutput decide(PreparationStageOutput.Prepared prepared, EndpointMatch.Unique unique,
            ClientVerificationOutcome clientVerification,
            SubjectAuthenticationOutcome subjectAuthentication) {
        RequestEvidence evidence = prepared.evidence();
        AccessRequest request = new AccessRequest(unique.endpoint().getId(),
                evidence.declaredAppId(), evidence.declaredClientId(),
                evidence.origin(), evidence.unsafe());
        AccessDecision decision = evaluator.evaluate(
                prepared.snapshot(), request, clientVerification, subjectAuthentication);
        if (decision instanceof AccessDecision.Denied d) {
            return new AccessStageOutput.Denied(d);
        }
        AccessDecision.Allowed allowed = (AccessDecision.Allowed) decision;
        return new AccessStageOutput.Allowed(allowed, prepared);
    }

    private AccessStageOutput denied(PreparationStageOutput.Prepared prepared, AccessDenyReason reason) {
        return new AccessStageOutput.Denied((AccessDecision.Denied) AccessDecision.denied(
                reason, prepared.snapshot().version()));
    }
}
