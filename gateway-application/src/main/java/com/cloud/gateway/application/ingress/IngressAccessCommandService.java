package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.IngressAccessDecision;
import com.cloud.gateway.domain.access.IngressAccessEvaluator;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.app.AppPolicy;
import com.cloud.gateway.domain.endpoint.EndpointPolicy;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;
import com.cloud.gateway.domain.policy.bundle.EffectivePolicy;
import com.cloud.gateway.domain.policy.bundle.PolicyResolver;
import com.cloud.gateway.domain.policy.capability.RateLimitDimension;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.security.SubjectAuthenticationSelection;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/** HTTP-neutral ingress use case. Adapters retain asynchronous subscription and cancellation. */
@RequiredArgsConstructor
public final class IngressAccessCommandService {
    private static final String SUBJECT_DIMENSION_SEPARATOR = ":";

    private final ClientCredentialAuthenticator clientAuthenticator;
    private final IngressAccessEvaluator evaluator;
    private final PolicyResolver policies;
    private final Clock clock;

    /** Select only the subject authentication required by the captured endpoint policy. */
    public CompletionStage<SubjectAuthenticationOutcome> authenticateSubject(SubjectAuthencationRequirement requirement,
                                                                             Supplier<CompletionStage<SubjectAuthenticationOutcome>> host,
                                                                             Supplier<CompletionStage<SubjectAuthenticationOutcome>> browser) {
        return switch (SubjectAuthenticationSelection.from(requirement)) {
            case IGNORE -> CompletableFuture.completedFuture(SubjectAuthenticationOutcome.absent());
            case HOST -> host.get();
            case BROWSER -> browser.get();
        };
    }

    /** Invoke at subscription time; null evidence means missing signature fields. */
    public CompletionStage<ClientVerificationOutcome> authenticateClient(
            IngressAccessCommand command, ClientSignatureEvidence evidence) {
        EndpointPolicy endpointPolicy = command.snapshot().content().endpointPolicies()
                .get(command.endpoint().endpointId());
        if (endpointPolicy.clientVerificationMode() != ClientVerificationMode.REQUEST_SIGNATURE
                || command.clientId() == null) {
            return CompletableFuture.completedFuture(ClientVerificationOutcome.absent());
        }
        ClientPolicy client = command.snapshot().content().clients().get(command.clientId());
        if (client == null || client.credentials() == null) {
            return CompletableFuture.completedFuture(ClientVerificationOutcome.rejected());
        }
        if (evidence == null) {
            return CompletableFuture.completedFuture(ClientVerificationOutcome.absent());
        }
        try {
            return clientAuthenticator.authenticate(client.credentials(), evidence, clock.instant());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(ClientVerificationOutcome.rejected());
        }
    }

    public IngressAccessOutput evaluate(IngressAccessCommand command,
            ClientVerificationOutcome clientVerification,
            SubjectAuthenticationOutcome subjectAuthentication) {
        AppPolicy appPolicy = command.appId() == null ? null
                : command.snapshot().content().appPolicies().get(command.appId());
        ClientPolicy clientPolicy = command.clientId() == null ? null
                : command.snapshot().content().clients().get(command.clientId());
        EndpointPolicy endpointPolicy = command.snapshot().content().endpointPolicies()
                .get(command.endpoint().endpointId());
        IngressAccessDecision decision = evaluator.evaluate(command.snapshot().version(), command.clientId(),
                appPolicy, clientPolicy, endpointPolicy,
                command.origin(), command.unsafe(),
                clientVerification, subjectAuthentication);
        if (decision instanceof IngressAccessDecision.Denied denied) {
            return new IngressAccessOutput.Denied(denied);
        }
        IngressAccessGrantedContext context = ((IngressAccessDecision.Allowed) decision).context();
        EffectivePolicy effective = policies.resolve(command.snapshot().content().policies(), command.endpoint(),
                context.appContext().appId(),
                context.clientContext() == null ? null : context.clientContext().clientId(),
                context.subjectContext() == null ? null : context.subjectContext().subjectType(), endpointPolicy);
        Map<RateLimitDimension, String> dimensions = new EnumMap<>(RateLimitDimension.class);
        dimensions.put(RateLimitDimension.IP, command.remoteIp());
        dimensions.put(RateLimitDimension.APP, context.appContext().appId().value());
        dimensions.put(RateLimitDimension.ENDPOINT, command.endpoint().endpointId().value());
        if (context.clientContext() != null) {
            dimensions.put(RateLimitDimension.CLIENT, context.clientContext().clientId().value());
        }
        if (context.subjectContext() != null) {
            dimensions.put(RateLimitDimension.SUBJECT,
                    context.subjectContext().subjectType().name() + SUBJECT_DIMENSION_SEPARATOR
                            + context.subjectContext().subjectId());
        }
        return new IngressAccessOutput.Allowed(context, effective, effective.headers(), dimensions);
    }
}
