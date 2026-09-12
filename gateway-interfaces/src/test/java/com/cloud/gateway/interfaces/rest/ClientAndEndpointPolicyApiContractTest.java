package com.cloud.gateway.interfaces.rest;

import com.cloud.gateway.api.enums.ClientStatusApiEnum;
import com.cloud.gateway.api.enums.EndpointStatusApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;
import com.cloud.gateway.api.policy.SaveClientApiCommand;
import com.cloud.gateway.api.policy.SaveEndpointApiCommand;
import com.cloud.gateway.application.policy.SaveClientAccessPolicyCommand;
import com.cloud.gateway.application.policy.SaveClientCredentialPolicyCommand;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.endpoint.EndpointStatus;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.interfaces.facade.mapper.GatewayPolicyApiMapper;
import com.cloud.gateway.interfaces.facade.mapper.mapstruct.GatewayPolicyApiMapStructMapper;
import com.cloud.gateway.interfaces.rest.mapstruct.GatewayPolicyAdminRestMapStructMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

class ClientAndEndpointPolicyApiContractTest {
    private final GatewayPolicyApiMapper domainMapper = Mappers.getMapper(GatewayPolicyApiMapStructMapper.class);
    private final GatewayPolicyAdminRestMapper restMapper = Mappers.getMapper(GatewayPolicyAdminRestMapStructMapper.class);

    @Test
    void shouldMapExplicitEndpointStatusThroughHttpApiAndDomain() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        GatewayPolicyAdminRequest.SaveEndpointAccessPolicy request = json.readValue("""
                {"expectedSourceRevision":"revision-1","endpointId":"PUBLIC",
                 "clientCredentialRequirement":"IGNORE","clientRequirement":"OPTIONAL",
                 "subjectAuthencationRequirement":"IGNORE","requiredScopes":[],"requiredAudiences":[],
                 "credentialForwarding":{"authorizationHeader":"STRIP","cookies":"STRIP_ALL","allowedCookieNames":[]},
                 "status":"DISABLED"}
                """, GatewayPolicyAdminRequest.SaveEndpointAccessPolicy.class);
        SaveEndpointApiCommand api = restMapper.toCommand(request);
        assertEquals(EndpointStatusApiEnum.DISABLED, api.status());
        assertEquals(EndpointStatus.DISABLED, domainMapper.toCommand(api).policy().status());
        assertFalse(domainMapper.toCommand(api).policy().enabled());
        request.setStatus(EndpointStatusApiEnum.ENABLED);
        assertTrue(domainMapper.toCommand(restMapper.toCommand(request)).policy().enabled());
    }

    @Test
    void shouldRequireEndpointStatusInsteadOfDefaultingMissingBoolean() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            GatewayPolicyAdminRequest.SaveEndpointAccessPolicy request = new GatewayPolicyAdminRequest.SaveEndpointAccessPolicy();
            assertEquals(1, factory.getValidator().validateProperty(request, "status").size());
            SaveEndpointApiCommand api = new SaveEndpointApiCommand("revision-1", "PUBLIC", null, null,
                    null, Set.of(), Set.of(), null, null);
            assertEquals(1, factory.getValidator().validateProperty(api, "status").size());
        }
    }

    @Test
    void shouldMapAccessOperationIntoMergedClientPolicyWithoutReplacingCredentials() {
        SaveClientApiCommand api = new SaveClientApiCommand("revision-1", "client-a", Set.of("app-a"), ClientStatusApiEnum.DISABLED);
        SaveClientAccessPolicyCommand command = domainMapper.toCommand(api);
        assertEquals("revision-1", command.expectedSourceRevision());
        assertEquals(new ClientId("client-a"), command.policy().clientId());
        assertEquals(Set.of(new AppId("app-a")), command.policy().allowedApps());
        assertEquals(ClientStatus.DISABLED, command.policy().status());
        // The access use case preserves stored credentials; this payload only carries access fields.
        assertNull(command.policy().credentials());
    }

    @Test
    void shouldMapCredentialOperationToClientAndOwnedCredentialValue() {
        SaveClientCredentialPolicyApiCommand api = new SaveClientCredentialPolicyApiCommand("revision-2", "client-a", credential("active"), credential("grace"));
        SaveClientCredentialPolicyCommand command = domainMapper.toCommand(api);
        assertEquals("revision-2", command.expectedSourceRevision());
        assertEquals(new ClientId("client-a"), command.clientId());
        assertEquals(command.clientId(), command.credentials().activeCredential().clientId());
        assertEquals(command.clientId(), command.credentials().graceCredential().clientId());
        assertEquals("active", command.credentials().activeCredential().credentialId().value());
        assertEquals("grace", command.credentials().graceCredential().credentialId().value());
    }

    @Test
    void shouldMapNestedCredentialHttpFieldsWithoutIntroducingClearSemantics() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        GatewayPolicyAdminRequest.SaveClientCredentialPolicy request = json.readValue("""
                {"expectedSourceRevision":"revision-2","clientId":"client-a",
                 "activeCredential":{"credentialId":"active","validFrom":"2026-01-01T00:00:00Z",
                  "expiresAt":"2099-01-01T00:00:00Z",
                  "signaturePayloadContract":"LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256","secretRef":"secret-reference"}}
                """, GatewayPolicyAdminRequest.SaveClientCredentialPolicy.class);
        SaveClientCredentialPolicyApiCommand api = restMapper.toCommand(request);
        assertEquals("revision-2", api.expectedSourceRevision());
        assertEquals("client-a", api.clientId());
        assertEquals(credential("active"), api.activeCredential());
        assertNull(api.graceCredential());
        assertNull(domainMapper.toCommand(api).credentials().graceCredential());
    }

    @Test
    void shouldRejectMissingActiveCredentialRatherThanClearStoredCredentials() {
        SaveClientCredentialPolicyApiCommand api = new SaveClientCredentialPolicyApiCommand("revision-2", "client-a", null, credential("grace"));
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertEquals(1, factory.getValidator().validateProperty(api, "activeCredential").size());
            GatewayPolicyAdminRequest.SaveClientCredentialPolicy request = new GatewayPolicyAdminRequest.SaveClientCredentialPolicy();
            assertEquals(1, factory.getValidator().validateProperty(request, "activeCredential").size());
        }
        assertThrows(DomainException.class, () -> domainMapper.toCommand(api));
    }

    private static ClientCredentialApiCommand credential(String id) {
        return new ClientCredentialApiCommand(id, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2099-01-01T00:00:00Z"), SignatureContractApiEnum.LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256,
                "secret-reference");
    }
}
