package com.cloud.gateway.domain.access;

import com.cloud.gateway.domain.access.client.ClientVerificationMode;
import com.cloud.gateway.domain.access.client.ClientVerificationOutcome;
import com.cloud.gateway.domain.access.client.VerifiedClient;
import com.cloud.gateway.domain.access.subject.AuthenticatedSubject;
import com.cloud.gateway.domain.access.subject.SessionId;
import com.cloud.gateway.domain.access.subject.SubjectAuthenticationOutcome;
import com.cloud.gateway.domain.access.subject.SubjectId;
import com.cloud.gateway.domain.app.*;
import com.cloud.gateway.domain.client.*;
import com.cloud.gateway.domain.endpoint.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.cloud.gateway.domain.policy.CredentialForwarding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class IngressAccessEvaluatorIdentityTest {
    private static final AppId APP = new AppId("app");
    private static final ClientId CLIENT = new ClientId("client");
    private static final EndpointId ENDPOINT = new EndpointId("endpoint");
    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private final IngressAccessEvaluator evaluator = new IngressAccessEvaluator();

    @Test
    void shouldIgnoreVerificationOutcomesWhenClientVerificationIsNotConfigured() {
        for (ClientVerificationRequirement requirement : ClientVerificationRequirement.values()) {
            for (ClientVerificationOutcome outcome : List.of(
                    verified(new ClientId("different-client")), ClientVerificationOutcome.absent(),
                    ClientVerificationOutcome.rejected(), ClientVerificationOutcome.failed())) {
                IngressAccessSnapshot snapshot = snapshot(requirement, null, SubjectAuthencationRequirement.IGNORE,
                        AppStatus.ENABLED, ClientStatus.ENABLED, EndpointStatus.ENABLED);
                ClientId requested = requirement == ClientVerificationRequirement.FORBIDDEN ? null : CLIENT;
                IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class,
                        evaluator.evaluate(snapshot, new IngressAccessRequest(ENDPOINT, APP, requested, null, false),
                                outcome, browser()));
                assertNull(allowed.context().client());
                assertNull(allowed.context().subject());
            }
        }
    }

    @Test
    void shouldRetainVerifiedIdentityOnlyForRequiredSignatureAndMatchingClient() {
        IngressAccessSnapshot snapshot = snapshot(ClientVerificationRequirement.REQUIRED, ClientVerificationMode.REQUEST_SIGNATURE,
                SubjectAuthencationRequirement.IGNORE, AppStatus.ENABLED, ClientStatus.ENABLED, EndpointStatus.ENABLED);
        IngressAccessRequest request = new IngressAccessRequest(ENDPOINT, APP, CLIENT, null, false);
        IngressAccessDecision.Allowed allowed = assertInstanceOf(IngressAccessDecision.Allowed.class,
                evaluator.evaluate(snapshot, request, verified(CLIENT), SubjectAuthenticationOutcome.absent()));
        assertEquals(CLIENT, allowed.context().client().clientId());
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot, request, verified(new ClientId("different-client")),
                        SubjectAuthenticationOutcome.absent()));
        assertEquals(IngressAccessDenyReason.CLIENT_VERIFICATION_MISMATCH, denied.reason());
    }

    @Test
    void shouldDenyEveryUnsuccessfulRequiredVerification() {
        IngressAccessSnapshot snapshot = snapshot(ClientVerificationRequirement.REQUIRED, ClientVerificationMode.REQUEST_SIGNATURE,
                SubjectAuthencationRequirement.IGNORE, AppStatus.ENABLED, ClientStatus.ENABLED, EndpointStatus.ENABLED);
        List<ClientVerificationOutcome> outcomes = List.of(ClientVerificationOutcome.absent(),
                ClientVerificationOutcome.rejected(), ClientVerificationOutcome.failed());
        List<IngressAccessDenyReason> reasons = List.of(IngressAccessDenyReason.CLIENT_VERIFICATION_REQUIRED,
                IngressAccessDenyReason.CLIENT_VERIFICATION_MISMATCH,
                IngressAccessDenyReason.CLIENT_VERIFICATION_FAILED);
        for (int index = 0; index < outcomes.size(); index++) {
            IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                    evaluator.evaluate(snapshot, new IngressAccessRequest(ENDPOINT, APP, CLIENT, null, false),
                            outcomes.get(index), SubjectAuthenticationOutcome.absent()));
            assertEquals(reasons.get(index), denied.reason());
        }
    }

    @Test
    void shouldTreatDisabledAggregatesAsNotFound() {
        assertDenied(snapshot(ClientVerificationRequirement.REQUIRED, null, SubjectAuthencationRequirement.IGNORE,
                AppStatus.DISABLED, ClientStatus.ENABLED, EndpointStatus.ENABLED), IngressAccessDenyReason.APP_NOT_FOUND);
        assertDenied(snapshot(ClientVerificationRequirement.REQUIRED, null, SubjectAuthencationRequirement.IGNORE,
                AppStatus.ENABLED, ClientStatus.DISABLED, EndpointStatus.ENABLED), IngressAccessDenyReason.CLIENT_NOT_FOUND);
        assertDenied(snapshot(ClientVerificationRequirement.REQUIRED, null, SubjectAuthencationRequirement.IGNORE,
                AppStatus.ENABLED, ClientStatus.ENABLED, EndpointStatus.DISABLED), IngressAccessDenyReason.ENDPOINT_NOT_FOUND);
    }

    @Test
    void shouldRequireAllowedOriginOnlyForUnsafeBrowserRequests() {
        IngressAccessSnapshot snapshot = snapshot(ClientVerificationRequirement.FORBIDDEN, null,
                SubjectAuthencationRequirement.BROWSER_SESSION_REQUIRED, AppStatus.ENABLED, ClientStatus.ENABLED, EndpointStatus.ENABLED);
        for (String origin : List.of("https://evil.example", "")) {
            IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                    evaluator.evaluate(snapshot, new IngressAccessRequest(ENDPOINT, APP, null, origin, true),
                            ClientVerificationOutcome.absent(), browser()));
            assertEquals(IngressAccessDenyReason.BROWSER_ORIGIN_NOT_ALLOWED, denied.reason());
        }
        assertInstanceOf(IngressAccessDecision.Allowed.class, evaluator.evaluate(snapshot,
                new IngressAccessRequest(ENDPOINT, APP, null, "https://app.example", true),
                ClientVerificationOutcome.absent(), browser()));
        assertInstanceOf(IngressAccessDecision.Allowed.class, evaluator.evaluate(snapshot,
                new IngressAccessRequest(ENDPOINT, APP, null, null, false),
                ClientVerificationOutcome.absent(), browser()));
    }

    private void assertDenied(IngressAccessSnapshot snapshot, IngressAccessDenyReason reason) {
        IngressAccessDecision.Denied denied = assertInstanceOf(IngressAccessDecision.Denied.class,
                evaluator.evaluate(snapshot, new IngressAccessRequest(ENDPOINT, APP, CLIENT, null, false),
                        ClientVerificationOutcome.absent(), SubjectAuthenticationOutcome.absent()));
        assertEquals(reason, denied.reason());
    }

    private static ClientVerificationOutcome verified(ClientId clientId) {
        return ClientVerificationOutcome.verified(new VerifiedClient(clientId, new CredentialId("credential"),
                ClientVerificationMode.REQUEST_SIGNATURE, NOW));
    }

    private static SubjectAuthenticationOutcome browser() {
        return SubjectAuthenticationOutcome.authenticated(new AuthenticatedSubject(SubjectType.BROWSER_SESSION,
                new SubjectId("subject"), new SessionId("session"), null, null, null, NOW.plusSeconds(600)));
    }

    private static IngressAccessSnapshot snapshot(ClientVerificationRequirement requirement, ClientVerificationMode mode,
                                                  SubjectAuthencationRequirement subject, AppStatus appStatus, ClientStatus clientStatus, EndpointStatus endpointStatus) {
        App app = new App(APP, "WEB", appStatus, new AppPolicy(Set.of("https://app.example")));
        Credential credential = new Credential(new CredentialId("credential"), "secret-ref",
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, NOW.minusSeconds(60), NOW.plusSeconds(600));
        Client client = new Client(CLIENT, clientStatus,
                new ClientPolicy(Set.of(APP), new Credentials(credential, null)));
        Endpoint endpoint = new Endpoint(ENDPOINT, new ServiceId("service"), new RouteId("route"),
                List.of(new EndpointMatcher("GET", "/resource")), Set.of(), endpointStatus,
                new EndpointPolicy(requirement, mode, subject, Set.of(), Set.of(), CredentialForwarding.stripAll()));
        return new IngressAccessSnapshot(1, new IngressAccessContent(new AppRegistry(List.of(app)),
                new ClientRegistry(List.of(client)), new EndpointRegistry(List.of(endpoint))), "hash", NOW);
    }
}
