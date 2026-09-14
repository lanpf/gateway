package com.cloud.gateway.application.ingress;

import com.cloud.gateway.application.security.ClientCredentialAuthenticator;
import com.cloud.gateway.application.security.ClientSignatureEvidence;
import com.cloud.gateway.domain.access.IngressAccessDecision;
import com.cloud.gateway.domain.access.IngressAccessDenyReason;
import com.cloud.gateway.domain.access.IngressAccessEvaluator;
import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.access.IngressAccessRequest;
import com.cloud.gateway.domain.access.RequestEvidence;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointMatch;
import com.cloud.gateway.domain.policy.bundle.EffectivePolicy;
import com.cloud.gateway.domain.policy.bundle.PolicyResolver;
import com.cloud.gateway.domain.policy.capability.RateLimitDimension;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;

/** HTTP-neutral access stage: composes verification and authentication outcomes into a decision. */
@RequiredArgsConstructor
public final class IngressAccessCommandService {
    private static final String SUBJECT_DIMENSION_SEPARATOR = ":";

    private final ClientCredentialAuthenticator clientAuthenticator;
    private final IngressAccessEvaluator evaluator;
    private final PolicyResolver policies;
    private final Clock clock;

    /** Verification runs only for a declared client with configured credentials and submitted evidence. */
    public CompletionStage<ClientVerificationOutcome> authenticateClient(
            IngressAccessCommand command, ClientSignatureEvidence evidence) {
        if (command.evidence().declaredClientId() == null) {
            return CompletableFuture.completedFuture(ClientVerificationOutcome.absent());
        }
        Client client = command.snapshot().content().clientRegistry()
                .find(command.evidence().declaredClientId()).orElse(null);
        Credentials credentials = client == null ? null : client.access().credentials();
        if (credentials == null || evidence == null) {
            return CompletableFuture.completedFuture(
                    credentials == null && evidence != null
                            ? ClientVerificationOutcome.rejected()
                            : ClientVerificationOutcome.absent());
        }
        try {
            return clientAuthenticator.authenticate(credentials, command.evidence().declaredClientId(), evidence, clock.instant());
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(ClientVerificationOutcome.rejected());
        }
    }

    public IngressAccessOutput evaluate(IngressAccessCommand command,
            ClientVerificationOutcome clientVerification,
            SubjectAuthenticationOutcome subjectAuthentication) {
        if (!(command.endpointMatch() instanceof EndpointMatch.Unique unique)) {
            return denied(command, IngressAccessDenyReason.ENDPOINT_NOT_FOUND);
        }
        if (command.evidence().declaredAppId() == null) {
            return denied(command, IngressAccessDenyReason.APP_NOT_FOUND);
        }
        IngressAccessRequest request = new IngressAccessRequest(unique.endpoint().getId(),
                command.evidence().declaredAppId(), command.evidence().declaredClientId(),
                command.evidence().origin(), command.evidence().unsafe());
        IngressAccessDecision decision = evaluator.evaluate(
                command.snapshot(), request, clientVerification, subjectAuthentication);
        if (decision instanceof IngressAccessDecision.Denied d) {
            return new IngressAccessOutput.Denied(d);
        }
        IngressAccessDecision.Allowed allowed = (IngressAccessDecision.Allowed) decision;
        IngressAccessGrantedContext context = allowed.grantedContext();
        EffectivePolicy effective = policies.resolve(command.snapshot().content().policies(), unique.endpoint(),
                context.app().appId(),
                context.client() == null ? null : context.client().clientId(),
                context.subject() == null ? null : context.subject().subjectType());
        return new IngressAccessOutput.Allowed(context, effective, effective.headers(),
                quotaDimensions(command, unique.endpoint(), context));
    }

    private Map<RateLimitDimension, String> quotaDimensions(IngressAccessCommand command, Endpoint endpoint,
            IngressAccessGrantedContext context) {
        Map<RateLimitDimension, String> dimensions = new EnumMap<>(RateLimitDimension.class);
        dimensions.put(RateLimitDimension.IP, command.evidence().remoteIp());
        dimensions.put(RateLimitDimension.APP, context.app().appId().value());
        dimensions.put(RateLimitDimension.ENDPOINT, endpoint.getId().value());
        if (context.client() != null) {
            dimensions.put(RateLimitDimension.CLIENT, context.client().clientId().value());
        }
        if (context.subject() != null) {
            dimensions.put(RateLimitDimension.SUBJECT, context.subject().subjectType().name()
                    + SUBJECT_DIMENSION_SEPARATOR + context.subject().subjectId().value());
        }
        return dimensions;
    }

    private IngressAccessOutput denied(IngressAccessCommand command, IngressAccessDenyReason reason) {
        return new IngressAccessOutput.Denied((IngressAccessDecision.Denied) IngressAccessDecision.denied(
                reason, command.snapshot().version()));
    }
}
