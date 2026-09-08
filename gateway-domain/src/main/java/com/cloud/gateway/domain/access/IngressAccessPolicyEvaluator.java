package com.cloud.gateway.domain.access;

import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ChannelCredentialRequirement;
import com.cloud.gateway.domain.policy.ChannelRequirement;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import com.cloud.gateway.domain.policy.SubjectRequirement;

public final class IngressAccessPolicyEvaluator {
    public IngressAccessDecision evaluate(
            long snapshotVersion,
            ChannelCode requestedChannelCode,
            IngressClientAppPolicy clientAppPolicy,
            IngressChannelAccessPolicy channelAccessPolicy,
            IngressRouteAccessPolicy routeAccessPolicy,
            String requestedOrigin,
            boolean unsafeRequest,
            ChannelCredentialAuthenticationOutcome channelCredentialAuthentication,
            SubjectAuthenticationOutcome subjectAuthentication
    ) {
        Require.notNull(channelCredentialAuthentication, DomainException::missingField);
        Require.notNull(subjectAuthentication, DomainException::missingField);
        if (clientAppPolicy == null) {
            return denied(IngressAccessDenyReason.CLIENT_APP_NOT_FOUND, snapshotVersion);
        }
        if (!clientAppPolicy.enabled()) {
            return denied(IngressAccessDenyReason.CLIENT_APP_DISABLED, snapshotVersion);
        }
        if (routeAccessPolicy == null) {
            return denied(IngressAccessDenyReason.ENDPOINT_NOT_FOUND, snapshotVersion);
        }
        if (!routeAccessPolicy.enabled()) {
            return denied(IngressAccessDenyReason.ENDPOINT_DISABLED, snapshotVersion);
        }
        if (unsafeRequest
                && routeAccessPolicy.subjectRequirement() == SubjectRequirement.BROWSER_SESSION_REQUIRED
                && !clientAppPolicy.allowsBrowserOrigin(requestedOrigin)) {
            return denied(IngressAccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, snapshotVersion);
        }

        IngressAccessDecision channelDecision = evaluateChannel(
                snapshotVersion, clientAppPolicy, channelAccessPolicy, routeAccessPolicy, requestedChannelCode);
        if (channelDecision != null) {
            return channelDecision;
        }

        if (routeAccessPolicy.channelCredentialRequirement() == ChannelCredentialRequirement.REQUEST_SIGNATURE) {
            if (channelCredentialAuthentication instanceof ChannelCredentialAuthenticationOutcome.Absent) {
                return denied(IngressAccessDenyReason.CHANNEL_CREDENTIAL_REQUIRED, snapshotVersion);
            }
            if (channelCredentialAuthentication instanceof ChannelCredentialAuthenticationOutcome.Rejected) {
                return denied(IngressAccessDenyReason.CHANNEL_CREDENTIAL_INVALID, snapshotVersion);
            }
            VerifiedChannelCredential verifiedChannelCredential =
                    ((ChannelCredentialAuthenticationOutcome.Authenticated) channelCredentialAuthentication).credential();
            if (!verifiedChannelCredential.channelCode().equals(requestedChannelCode)) {
                return denied(IngressAccessDenyReason.CHANNEL_CREDENTIAL_INVALID, snapshotVersion);
            }
        }

        IngressAccessDecision subjectDecision = evaluateSubject(
                snapshotVersion, routeAccessPolicy, subjectAuthentication);
        if (subjectDecision != null) {
            return subjectDecision;
        }

        AuthenticatedSubject authenticatedSubject = subjectAuthentication instanceof SubjectAuthenticationOutcome.Authenticated authenticated
                ? authenticated.subject() : null;
        VerifiedChannelCredential verifiedChannelCredential =
                channelCredentialAuthentication instanceof ChannelCredentialAuthenticationOutcome.Authenticated authenticated
                        ? authenticated.credential() : null;

        return new IngressAccessDecision.Allowed(new TrustedRequestContext(
                new TrustedRequestContext.ClientContext(clientAppPolicy.clientAppId(), clientAppPolicy.platform()),
                requestedChannelCode == null ? null : new TrustedRequestContext.ChannelContext(requestedChannelCode),
                verifiedChannelCredential == null ? null : new TrustedRequestContext.ChannelCredentialContext(
                        verifiedChannelCredential.credentialId(), verifiedChannelCredential.channelCode()),
                authenticatedSubject == null ? null : subjectContext(authenticatedSubject),
                snapshotVersion));
    }

    private static IngressAccessDecision evaluateChannel(
            long version,
            IngressClientAppPolicy client,
            IngressChannelAccessPolicy channel,
            IngressRouteAccessPolicy route,
            ChannelCode requestedChannelCode
    ) {
        boolean hasChannel = requestedChannelCode != null;
        if (route.channelRequirement() == ChannelRequirement.REQUIRED && !hasChannel) {
            return denied(IngressAccessDenyReason.CHANNEL_REQUIRED, version);
        }
        if (route.channelRequirement() == ChannelRequirement.FORBIDDEN && hasChannel) {
            return denied(IngressAccessDenyReason.CHANNEL_NOT_PERMITTED_FOR_ENDPOINT, version);
        }
        if (hasChannel && (channel == null || !channel.channelCode().equals(requestedChannelCode)
                || !channel.allows(client.clientAppId()))) {
            return denied(IngressAccessDenyReason.CHANNEL_NOT_ALLOWED, version);
        }
        return null;
    }

    private static IngressAccessDecision evaluateSubject(
            long version,
            IngressRouteAccessPolicy route,
            SubjectAuthenticationOutcome authentication
    ) {
        SubjectRequirement requirement = route.subjectRequirement();
        if (requirement == SubjectRequirement.IGNORE) {
            return null;
        }
        if (authentication instanceof SubjectAuthenticationOutcome.Rejected) {
            return denied(IngressAccessDenyReason.SUBJECT_AUTHENTICATION_FAILED, version);
        }
        if (authentication instanceof SubjectAuthenticationOutcome.Unavailable unavailable) {
            return denied(denyReason(unavailable.reason()), version);
        }
        if (authentication instanceof SubjectAuthenticationOutcome.Absent) {
            return requirement == SubjectRequirement.HOST_SESSION_OPTIONAL
                    ? null : denied(IngressAccessDenyReason.SUBJECT_REQUIRED, version);
        }
        AuthenticatedSubject subject = ((SubjectAuthenticationOutcome.Authenticated) authentication).subject();
        SubjectType requiredType = requirement == SubjectRequirement.BROWSER_SESSION_REQUIRED
                ? SubjectType.BROWSER_SESSION : SubjectType.HOST_SESSION;
        if (subject.subjectType() != requiredType) {
            return denied(IngressAccessDenyReason.SUBJECT_TYPE_MISMATCH, version);
        }
        if (!subject.audiences().containsAll(route.requiredAudiences())) {
            return denied(IngressAccessDenyReason.AUDIENCE_NOT_ALLOWED, version);
        }
        if (!subject.scopes().containsAll(route.requiredScopes())) {
            return denied(IngressAccessDenyReason.SCOPE_NOT_ALLOWED, version);
        }
        return null;
    }

    private static TrustedRequestContext.SubjectContext subjectContext(AuthenticatedSubject subject) {
        return new TrustedRequestContext.SubjectContext(subject.subjectType(), subject.subjectId(),
                subject.loginSessionId(), subject.audiences(), subject.scopes(), subject.credentialExpiresAt());
    }

    private static IngressAccessDenyReason denyReason(
            SubjectAuthenticationUnavailableReason unavailableReason
    ) {
        return switch (unavailableReason) {
            case DEPENDENCY_UNAVAILABLE ->
                    IngressAccessDenyReason.SUBJECT_AUTHENTICATION_DEPENDENCY_UNAVAILABLE;
            case INVALID_RESPONSE -> IngressAccessDenyReason.SUBJECT_AUTHENTICATION_RESPONSE_INVALID;
        };
    }

    private static IngressAccessDecision.Denied denied(IngressAccessDenyReason reason, long version) {
        return new IngressAccessDecision.Denied(reason, version);
    }
}
