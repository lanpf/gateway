package com.cloud.gateway.domain.access;

import com.cloud.framework.core.SubjectType;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationRejectReason;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.SubjectAuthencationRequirement;

public final class IngressAccessEvaluator {
    public IngressAccessDecision evaluate(
            IngressAccessSnapshot snapshot,
            IngressAccessRequest request,
            ClientVerificationOutcome clientVerification,
            SubjectAuthenticationOutcome subjectAuthentication
    ) {
        Require.notNull(snapshot, DomainException::missingField);
        Require.notNull(request, DomainException::missingField);
        Require.notNull(clientVerification, DomainException::missingField);
        Require.notNull(subjectAuthentication, DomainException::missingField);

        Endpoint endpoint = snapshot.content().endpointRegistry()
                .find(request.endpointId()).filter(Endpoint::enabled).orElse(null);
        if (endpoint == null) {
            return IngressAccessDecision.denied(IngressAccessDenyReason.ENDPOINT_NOT_FOUND, snapshot.version());
        }
        App app = snapshot.content().appRegistry().find(request.appId()).filter(App::enabled).orElse(null);
        if (app == null) {
            return IngressAccessDecision.denied(IngressAccessDenyReason.APP_NOT_FOUND, snapshot.version());
        }

        IngressAccessDenyReason clientDenyReason = evaluateClient(snapshot, request, clientVerification, endpoint);
        if (clientDenyReason != null) {
            return IngressAccessDecision.denied(clientDenyReason, snapshot.version());
        }
        VerifiedClient verifiedClient = clientVerification instanceof ClientVerificationOutcome.Verified verified
                ? verified.client() : null;


        IngressAccessDenyReason subjectDenyReason = evaluateSubject(subjectAuthentication, endpoint);
        if (subjectDenyReason != null) {
            return IngressAccessDecision.denied(subjectDenyReason, snapshot.version());
        }
        AuthenticatedSubject authenticatedSubject = null;
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Authenticated authenticated) {
            authenticatedSubject = authenticated.subject();
            if (authenticatedSubject.subjectType() == SubjectType.BROWSER_SESSION
                && request.unsafe() && !app.allowsOrigin(request.origin())) {
                return IngressAccessDecision.denied(IngressAccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, snapshot.version());
            }
        }

        return new IngressAccessDecision.Allowed(new IngressAccessGrantedContext(app, verifiedClient, authenticatedSubject), snapshot.version());
    }


    private static IngressAccessDenyReason evaluateClient(IngressAccessSnapshot snapshot, IngressAccessRequest request, ClientVerificationOutcome clientVerification, Endpoint endpoint) {
        if (request.clientId() == null) {
            return endpoint.accessPolicy().clientVerificationRequirement() == ClientVerificationRequirement.REQUIRED
                    ? IngressAccessDenyReason.CLIENT_REQUIRED : null;
        }
        // if a client is provided, it must be match the client policy and allowed apps
        Client client = snapshot.content().clientRegistry().find(request.clientId()).filter(Client::enabled).orElse(null);
        if (client == null) {
            return IngressAccessDenyReason.CLIENT_NOT_FOUND;
        }
        if (!client.allowsApp(request.appId())) {
            return IngressAccessDenyReason.CLIENT_NOT_ALLOWED;
        }

        if (clientVerification instanceof ClientVerificationOutcome.Absent) {
            return endpoint.accessPolicy().clientVerificationRequirement() == ClientVerificationRequirement.REQUIRED
                    ? IngressAccessDenyReason.CLIENT_VERIFICATION_REQUIRED : null;
        }
        if (clientVerification instanceof ClientVerificationOutcome.Verified verified) {
            return !verified.client().clientId().equals(request.clientId())
                    ? IngressAccessDenyReason.CLIENT_VERIFICATION_MISMATCH
                    : null;
        }
        if (clientVerification instanceof ClientVerificationOutcome.Rejected) {
            return IngressAccessDenyReason.CLIENT_VERIFICATION_REJECTED;
        }
        if (clientVerification instanceof ClientVerificationOutcome.Failed) {
            return IngressAccessDenyReason.CLIENT_VERIFICATION_FAILED;
        }
        return null;
    }


    private static IngressAccessDenyReason evaluateSubject(SubjectAuthenticationOutcome subjectAuthentication, Endpoint endpoint) {
        SubjectAuthencationRequirement requirement = endpoint.accessPolicy().subjectAuthencationRequirement();
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Absent) {
            return requirement == SubjectAuthencationRequirement.REQUIRED
                    ? IngressAccessDenyReason.SUBJECT_REQUIRED
                    : null;
        }
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Authenticated authenticated) {
            if (!authenticated.subject().audiences().containsAll(endpoint.accessPolicy().requiredAudiences())) {
                return IngressAccessDenyReason.AUDIENCE_NOT_ALLOWED;
            }
            if (!authenticated.subject().scopes().containsAll(endpoint.accessPolicy().requiredScopes())) {
                return IngressAccessDenyReason.SCOPE_NOT_ALLOWED;
            }
        }
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Rejected rejected) {
            return rejected.reason() == SubjectAuthenticationRejectReason.EXPIRED && requirement == SubjectAuthencationRequirement.OPTIONAL
                    ? null
                    : IngressAccessDenyReason.SUBJECT_AUTHENTICATION_REJECTED;
        }
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Failed failed) {
            return switch (failed.reason()) {
                case UPSTREAM_UNAVAILABLE -> IngressAccessDenyReason.SUBJECT_AUTHENTICATION_UPSTREAM_UNAVAILABLE;
                case RESPONSE_INVALID -> IngressAccessDenyReason.SUBJECT_AUTHENTICATION_RESPONSE_INVALID;
            };
        }
        return null;
    }
}
