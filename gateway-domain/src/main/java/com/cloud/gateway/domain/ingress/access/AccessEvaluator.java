package com.cloud.gateway.domain.ingress.access;

import com.cloud.gateway.domain.common.DomainException;

import com.cloud.framework.core.validation.Require;

import com.cloud.framework.core.SubjectType;
import com.cloud.gateway.domain.app.App;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.endpoint.ClientVerificationRequirement;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.SubjectAuthenticationRequirement;
import com.cloud.gateway.domain.ingress.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.ingress.access.client.VerifiedClient;
import com.cloud.gateway.domain.ingress.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.ingress.access.subject.SubjectAuthenticationRejectReason;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;

public final class AccessEvaluator {
    public AccessDecision evaluate(
            IngressSnapshot snapshot,
            AccessRequest request,
            ClientVerificationOutcome clientVerification,
            SubjectAuthenticationOutcome subjectAuthentication
    ) {
        Require.notNull(snapshot, DomainException::missingField);
        Require.notNull(request, DomainException::missingField);
        Require.notNull(clientVerification, DomainException::missingField);
        Require.notNull(subjectAuthentication, DomainException::missingField);

        Endpoint endpoint = snapshot.content().endpoints()
                .find(request.endpointId()).filter(Endpoint::enabled).orElse(null);
        if (endpoint == null) {
            return AccessDecision.denied(AccessDenyReason.ENDPOINT_NOT_FOUND, snapshot.version());
        }
        App app = snapshot.content().apps().find(request.appId()).filter(App::enabled).orElse(null);
        if (app == null) {
            return AccessDecision.denied(AccessDenyReason.APP_NOT_FOUND, snapshot.version());
        }
        VerifiedApp verifiedApp = new VerifiedApp(app.appId(), app.platform());

        AccessDenyReason clientDenyReason = evaluateClient(snapshot, request, clientVerification, endpoint);
        if (clientDenyReason != null) {
            return AccessDecision.denied(clientDenyReason, snapshot.version());
        }
        VerifiedClient verifiedClient = clientVerification instanceof ClientVerificationOutcome.Verified verified
                ? verified.client() : null;


        AccessDenyReason subjectDenyReason = evaluateSubject(subjectAuthentication, endpoint);
        if (subjectDenyReason != null) {
            return AccessDecision.denied(subjectDenyReason, snapshot.version());
        }
        AuthenticatedSubject authenticatedSubject = null;
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Authenticated authenticated) {
            authenticatedSubject = authenticated.subject();
            if (authenticatedSubject.subjectType() == SubjectType.BROWSER_SESSION
                && request.unsafe() && !app.allowsOrigin(request.origin())) {
                return AccessDecision.denied(AccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, snapshot.version());
            }
        }

        return new AccessDecision.Allowed(new AccessGrantedContext(verifiedApp, verifiedClient, authenticatedSubject), snapshot.version());
    }


    private static AccessDenyReason evaluateClient(IngressSnapshot snapshot, AccessRequest request, ClientVerificationOutcome clientVerification, Endpoint endpoint) {
        if (request.clientId() == null) {
            if (endpoint.access().clientVerificationRequirement() == ClientVerificationRequirement.REQUIRED) {
                return AccessDenyReason.CLIENT_REQUIRED;
            }
            if (!(clientVerification instanceof ClientVerificationOutcome.Absent)) {
                return AccessDenyReason.CLIENT_VERIFICATION_MISMATCH;
            }
            return null;
        }
        // if a client is provided, it must be match the client policy and allowed apps
        Client client = snapshot.content().clients().find(request.clientId()).filter(Client::enabled).orElse(null);
        if (client == null) {
            return AccessDenyReason.CLIENT_NOT_FOUND;
        }
        if (!client.allowsApp(request.appId())) {
            return AccessDenyReason.CLIENT_NOT_ALLOWED;
        }

        if (clientVerification instanceof ClientVerificationOutcome.Absent) {
            if (endpoint.access().clientVerificationRequirement() == ClientVerificationRequirement.REQUIRED) {
                return AccessDenyReason.CLIENT_VERIFICATION_REQUIRED;
            }
        }
        if (clientVerification instanceof ClientVerificationOutcome.Verified verified) {
            if (!verified.client().clientId().equals(request.clientId())) {
                return AccessDenyReason.CLIENT_VERIFICATION_MISMATCH;
            }
        }
        if (clientVerification instanceof ClientVerificationOutcome.Rejected) {
            return AccessDenyReason.CLIENT_VERIFICATION_REJECTED;
        }
        if (clientVerification instanceof ClientVerificationOutcome.Failed) {
            return AccessDenyReason.CLIENT_VERIFICATION_FAILED;
        }
        return null;
    }


    private static AccessDenyReason evaluateSubject(SubjectAuthenticationOutcome subjectAuthentication, Endpoint endpoint) {
        SubjectAuthenticationRequirement requirement = endpoint.access().subjectAuthenticationRequirement();
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Absent) {
            if (requirement == SubjectAuthenticationRequirement.REQUIRED) {
                return AccessDenyReason.SUBJECT_REQUIRED;
            }
        }
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Authenticated authenticated) {
            if (!authenticated.subject().audiences().containsAll(endpoint.access().requiredAudiences())) {
                return AccessDenyReason.AUDIENCE_NOT_ALLOWED;
            }
            if (!authenticated.subject().scopes().containsAll(endpoint.access().requiredScopes())) {
                return AccessDenyReason.SCOPE_NOT_ALLOWED;
            }
        }
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Rejected rejected) {
            return rejected.reason() == SubjectAuthenticationRejectReason.EXPIRED && requirement == SubjectAuthenticationRequirement.ANONYMOUS
                    ? null
                    : AccessDenyReason.SUBJECT_AUTHENTICATION_REJECTED;
        }
        if (subjectAuthentication instanceof SubjectAuthenticationOutcome.Failed failed) {
            return switch (failed.reason()) {
                case UPSTREAM_UNAVAILABLE -> AccessDenyReason.SUBJECT_AUTHENTICATION_UPSTREAM_UNAVAILABLE;
                case RESPONSE_INVALID -> AccessDenyReason.SUBJECT_AUTHENTICATION_RESPONSE_INVALID;
            };
        }
        return null;
    }
}
