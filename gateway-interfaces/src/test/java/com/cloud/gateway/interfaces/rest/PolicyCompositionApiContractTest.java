package com.cloud.gateway.interfaces.rest;

import com.cloud.gateway.api.policy.PolicyCompositionApiCommand;
import com.cloud.gateway.api.policy.SavePolicyCompositionApiCommand;
import com.cloud.gateway.domain.policy.bundle.PolicyBundleId;
import com.cloud.framework.core.RequestHeader;
import com.cloud.gateway.interfaces.facade.mapper.DefaultPolicyCompositionApiMapper;
import com.cloud.gateway.interfaces.rest.mapstruct.PolicyCompositionRestMapStructMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.junit.jupiter.api.Assertions.*;

class PolicyCompositionApiContractTest {
    @Test void shouldMapTypedHttpConfigurationThroughApiIntoValidatedDomain() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        GatewayPolicyAdminRequest.SavePolicyComposition request = json.readValue("""
                {"expectedSourceRevision":"revision-1","policies":{
                  "bundles":{"standard":{"bundleId":"standard",
                    "timeout":{"connect":"PT1S","read":"PT2S","total":"PT3S"},
                    "headers":{"trustedHeaders":["APP_ID"],
                      "setHeaders":{},"removeHeaders":[],
                      "credentialForwarding":{"authorizationHeader":"STRIP","cookies":"STRIP_ALL","allowedCookieNames":[]}}}},
                  "bindings":[{"bindingId":"default","bundleId":"standard","tags":[]}]
                }}
                """, GatewayPolicyAdminRequest.SavePolicyComposition.class);
        SavePolicyCompositionApiCommand api = Mappers.getMapper(PolicyCompositionRestMapStructMapper.class).toCommand(request);
        com.cloud.gateway.application.policy.SavePolicyCompositionCommand command = new DefaultPolicyCompositionApiMapper().toCommand(api);
        assertEquals("revision-1", command.expectedSourceRevision());
        assertEquals(Duration.ofSeconds(3), command.policies().bundles().get(new PolicyBundleId("standard")).timeout().total());
        assertEquals(java.util.Set.of(RequestHeader.APP_ID),
                command.policies().bundles().get(new PolicyBundleId("standard")).headers().trustedHeaders());
        assertEquals(new PolicyBundleId("standard"), command.policies().bindings().get(0).bundleId());
        assertThrows(UnsupportedOperationException.class, () -> api.policies().bundles().clear());
    }

    @Test void shouldRejectUnknownApiRateLimitModes() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> json.readValue("{\"ruleId\":\"ip\",\"dimension\":\"IP\",\"capacity\":10,\"refillPerSecond\":1,\"mode\":\"PREALLOCATED\"}",
                        PolicyCompositionApiCommand.Rule.class));
    }

    @Test void shouldRejectSignatureAndAppVersionAsTrustedSourcesDuringDomainConstruction() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        for (RequestHeader source : java.util.List.of(RequestHeader.SIGNATURE, RequestHeader.APP_VERSION)) {
            SavePolicyCompositionApiCommand api = json.readValue(configuration(source.name()), SavePolicyCompositionApiCommand.class);
            assertEquals(java.util.Set.of(source), api.policies().bundles().get("standard").headers().trustedHeaders());
            assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                    () -> new DefaultPolicyCompositionApiMapper().toCommand(api));
        }
    }

    @Test void shouldRejectRemovedSourceNamesAtJsonBoundary() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        for (String source : java.util.List.of("VERIFIED_CLIENT_ID", "SUBJECT_ID", "LEGACY_CLIENT_APP_ID")) {
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> json.readValue(configuration(source), SavePolicyCompositionApiCommand.class));
        }
    }

    @Test void shouldRejectOldMappingFieldEvenWhenOtherJsonUnknownFieldsAreIgnored() {
        var builder = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
        builder.featuresToDisable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        new HeaderPolicyJsonCustomizer().customize(builder);
        ObjectMapper json = builder.build();
        String bothFields = configuration("APP_ID").replace("\"trustedHeaders\":[\"APP_ID\"]",
                "\"trustedHeaders\":[\"APP_ID\"],\"trustedMappings\":{\"X-Gateway-App\":\"APP_ID\"}");
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> json.readValue(bothFields, SavePolicyCompositionApiCommand.class));
    }

    private static String configuration(String source) {
        return """
                {"expectedSourceRevision":"revision-1","policies":{
                  "bundles":{"standard":{"bundleId":"standard",
                    "headers":{"trustedHeaders":["%s"],
                      "setHeaders":{},"removeHeaders":[],
                      "credentialForwarding":{"authorizationHeader":"STRIP","cookies":"STRIP_ALL","allowedCookieNames":[]}}}},
                  "bindings":[]
                }}
                """.formatted(source);
    }
}
