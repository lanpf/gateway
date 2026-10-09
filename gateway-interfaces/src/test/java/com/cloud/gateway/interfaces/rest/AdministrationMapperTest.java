package com.cloud.gateway.interfaces.rest;

import com.cloud.gateway.domain.common.DomainException;
import static org.junit.jupiter.api.Assertions.*;
import com.cloud.gateway.api.enums.ClientVerificationRequirementApiEnum;
import com.cloud.gateway.api.enums.SubjectAuthenticationRequirementApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;
import com.cloud.gateway.api.facade.admin.definition.SaveAppApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveEndpointApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveClientCredentialApiCommand;
import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.*;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.interfaces.facade.admin.definition.AccessDefinitionApiMapper;
import com.cloud.gateway.interfaces.facade.admin.definition.mapstruct.AbstractAccessDefinitionApiMapStructMapperImpl;
import com.cloud.gateway.interfaces.rest.mapstruct.AbstractGatewayPolicyAdminRestMapStructMapperImpl;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdministrationMapperTest {
    private final GatewayPolicyAdminRestMapper rest = new AbstractGatewayPolicyAdminRestMapStructMapperImpl();
    private final AccessDefinitionApiMapper definitions = new AbstractAccessDefinitionApiMapStructMapperImpl();

    @Test
    void shouldUseTargetAppAndIsolateOrigins() {
        GatewayPolicyAdminRequest.SaveAppAccess request = new GatewayPolicyAdminRequest.SaveAppAccess();
        request.setTargetAppId("target");
        request.setPlatform("WEB");
        request.setExpectedSourceRevision("revision");
        request.getAllowedOrigins().add("https://one.example");
        SaveAppApiCommand command = rest.toCommand(request);
        request.getAllowedOrigins().clear();
        assertEquals("target", command.appId());
        assertEquals(Set.of("https://one.example"), command.allowedOrigins());
        assertThrows(UnsupportedOperationException.class, () -> command.allowedOrigins().clear());
        assertEquals("target", definitions.toCommand(command).appId().value());
    }

    @Test
    void shouldKeepMissingRevisionRejection() {
        assertThrows(FrameworkException.class, () -> definitions.toCommand(
                new SaveAppApiCommand(null, "app", "WEB", Set.of())));
    }

    @Test
    void shouldMapNestedRestCredentialAndNullableGrace() {
        GatewayPolicyAdminRequest.ClientCredential credential = new GatewayPolicyAdminRequest.ClientCredential();
        credential.setCredentialId("key");
        credential.setSecretRef("secret-ref");
        credential.setSignatureContract(SignatureContractApiEnum.values()[0]);
        credential.setValidFrom(Instant.parse("2026-01-01T00:00:00Z"));
        credential.setExpiresAt(credential.getValidFrom().plusSeconds(3600));
        GatewayPolicyAdminRequest.SaveClientCredential request = new GatewayPolicyAdminRequest.SaveClientCredential();
        request.setExpectedSourceRevision("revision");
        request.setClientId("client");
        request.setActiveCredential(credential);
        var command = rest.toCommand(request);
        assertEquals("key", command.activeCredential().credentialId());
        assertEquals("secret-ref", command.activeCredential().secretRef());
        assertEquals(credential.getExpiresAt(), command.activeCredential().expiresAt());
        assertNull(command.graceCredential());
    }

    @Test
    void shouldMapSnapshotVersionThroughItsValueObject() {
        var mapper = new com.cloud.gateway.interfaces.facade.admin.snapshot.mapstruct.AbstractAccessSnapshotApiMapStructMapperImpl();
        var command = mapper.toCommand(new com.cloud.gateway.api.facade.admin.snapshot.RollbackSnapshotApiCommand(7));
        assertEquals(7, command.targetSnapshotVersion().value());
        assertThrows(FrameworkException.class, () -> mapper.toCommand(
                new com.cloud.gateway.api.facade.admin.snapshot.RollbackSnapshotApiCommand(0)));
    }

    @Test
    void shouldKeepRestMatcherProjectionImmutableAndDetached() {
        GatewayPolicyAdminRequest.SaveEndpointAccess request = new GatewayPolicyAdminRequest.SaveEndpointAccess();
        GatewayPolicyAdminRequest.Matcher matcher = new GatewayPolicyAdminRequest.Matcher();
        matcher.setHttpMethod("GET");
        matcher.setPathPattern("/test");
        request.getMatchers().add(matcher);
        var command = rest.toCommand(request);
        matcher.setPathPattern("/changed");
        request.getMatchers().clear();
        assertEquals("/test", command.matchers().get(0).pathPattern());
        assertThrows(UnsupportedOperationException.class, () -> command.matchers().clear());
    }

    @Test
    void shouldNormalizeOptionalEndpointSetsThroughDomainConstruction() {
        SaveEndpointApiCommand command = new SaveEndpointApiCommand("revision", "TEST.ENDPOINT",
                "service", "route", List.of(new SaveEndpointApiCommand.MatcherApiCommand("GET", "/test")), Set.of(),
                ClientVerificationRequirementApiEnum.OPTIONAL,
                SubjectAuthenticationRequirementApiEnum.ANONYMOUS, null, null);
        var mapped = definitions.toCommand(command);
        assertEquals(Set.of(), mapped.requiredScopes());
        assertEquals(Set.of(), mapped.requiredAudiences());
        assertEquals("service", mapped.serviceId().value());
        assertEquals("route", mapped.routeId().value());
        assertEquals("/test", mapped.matchers().get(0).pathPattern());
    }

    @Test
    void shouldPreserveNullableGraceAndValidateCredentialInterval() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = start.plusSeconds(3600);
        SignatureContractApiEnum contract = SignatureContractApiEnum.values()[0];
        var active = new SaveClientCredentialApiCommand.CredentialApiCommand("key", "secret-ref", contract, start, end);
        var mapped = definitions.toCommand(new SaveClientCredentialApiCommand("revision", "client", active, null));
        assertNull(mapped.credentials().graceCredential());
        assertEquals("secret-ref", mapped.credentials().activeCredential().secretRef());
        var invalid = new SaveClientCredentialApiCommand.CredentialApiCommand("key", "secret-ref", contract, end, start);
        assertThrows(DomainException.class, () -> definitions.toCommand(
                new SaveClientCredentialApiCommand("revision", "client", invalid, null)));
    }

    @Test
    void shouldProjectCredentialsWithoutSecrets() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Credential credential = new Credential(new CredentialId("key"), "never-return-this",
                SignatureContract.values()[0], start, start.plusSeconds(3600));
        Client client = new Client(new ClientId("client"), ClientStatus.ENABLED,
                new ClientAccess(Set.of(new AppId("app")), new Credentials(credential, null)));
        var view = definitions.clients(new RevisionedAccessDefinition<>(new ClientRegistry(List.of(client)),
                new Revision("revision")));
        assertEquals("revision", view.sourceRevision());
        assertEquals("key", view.clients().iterator().next().activeCredential().credentialId());
        assertFalse(view.toString().contains("never-return-this"));
        assertNull(view.clients().iterator().next().graceCredential());
    }
}
