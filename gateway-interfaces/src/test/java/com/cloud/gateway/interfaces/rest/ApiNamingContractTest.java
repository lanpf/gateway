package com.cloud.gateway.interfaces.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.api.facade.admin.definition.AppsApiView;
import com.cloud.gateway.api.facade.admin.definition.ClientsApiView;
import com.cloud.gateway.api.facade.admin.definition.EndpointsApiView;
import com.cloud.gateway.api.facade.admin.definition.SaveEndpointApiCommand;
import com.cloud.gateway.api.facade.admin.snapshot.SnapshotApiView;
import com.cloud.gateway.api.facade.admin.snapshot.SnapshotUpdateApiOutput;
import com.cloud.gateway.api.facade.admin.definition.CapabilityPolicyConfigApiCommand;
import com.cloud.gateway.api.facade.admin.definition.SaveCapabilityPolicyConfigApiCommand;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class ApiNamingContractTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldKeepSnapshotOutputAndViewWireShapeIdentical() throws Exception {
        Instant publishedAt = Instant.parse("2026-01-01T00:00:00Z");
        SnapshotUpdateApiOutput output = new SnapshotUpdateApiOutput(7, "hash", publishedAt);
        SnapshotApiView view = new SnapshotApiView(7, "hash", publishedAt);
        assertEquals(json.valueToTree(output), json.valueToTree(view));
        assertEquals(Set.of("snapshotVersion", "contentHash", "publishedAt"),
                json.convertValue(output, java.util.Map.class).keySet());
        assertEquals(output, json.readValue(json.writeValueAsString(output), SnapshotUpdateApiOutput.class));
        assertEquals(view, json.readValue(json.writeValueAsString(view), SnapshotApiView.class));
    }

    @Test
    void shouldKeepNestedCapabilityJsonNames() throws Exception {
        String document = "{\"expectedSourceRevision\":\"r\",\"policies\":{\"bundles\":{},\"bindings\":{}}}";
        SaveCapabilityPolicyConfigApiCommand command = json.readValue(document, SaveCapabilityPolicyConfigApiCommand.class);
        assertEquals(json.readTree(document), json.valueToTree(command));
        CapabilityPolicyConfigApiCommand policyConfig = json.readValue(
                "{\"bundles\":{},\"bindings\":{}}", CapabilityPolicyConfigApiCommand.class);
        assertTrue(policyConfig.bundles().isEmpty());
        assertTrue(command.policies().bindings().isEmpty());
    }

    @Test
    void shouldPreserveAndDetachOpenCapabilityPayloads() throws Exception {
        String document = """
                {"bundleId":"example","capabilities":{"budget":{"units":8,"labels":["a"]}}}
                """;
        CapabilityPolicyConfigApiCommand.BundleApiCommand decoded = json.readValue(
                document, CapabilityPolicyConfigApiCommand.BundleApiCommand.class);
        assertEquals(json.readTree(document), json.valueToTree(decoded));
        SaveCapabilityPolicyConfigApiCommand.CapabilityPolicyConfigApiCommand.BundleApiCommand nested = json.readValue(
                document, SaveCapabilityPolicyConfigApiCommand.CapabilityPolicyConfigApiCommand.BundleApiCommand.class);
        assertEquals(json.readTree(document), json.valueToTree(nested));

        List<String> labels = new ArrayList<>(List.of("a"));
        java.util.Map<String, Object> node = new java.util.HashMap<>();
        node.put("labels", labels);
        java.util.Map<String, java.util.Map<String, Object>> nodes = new java.util.HashMap<>();
        nodes.put("budget", node);
        CapabilityPolicyConfigApiCommand.BundleApiCommand command =
                new CapabilityPolicyConfigApiCommand.BundleApiCommand("example", nodes);
        labels.clear();
        node.clear();
        nodes.clear();
        assertEquals(List.of("a"), command.capabilities().get("budget").get("labels"));
        assertThrows(UnsupportedOperationException.class, () -> command.capabilities().clear());
        assertThrows(UnsupportedOperationException.class, () -> command.capabilities().get("budget").clear());
        List<?> copied = (List<?>) command.capabilities().get("budget").get("labels");
        assertThrows(UnsupportedOperationException.class, copied::clear);
        assertThrows(IllegalArgumentException.class, () -> new CapabilityPolicyConfigApiCommand.BundleApiCommand(
                "example", java.util.Map.of("budget", java.util.Map.of("value", new Object()))));
    }

    @Test
    void shouldStillRejectUnknownHeaderProperties() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        builder.failOnUnknownProperties(false);
        new HeaderPolicyJsonCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();
        assertThrows(JsonMappingException.class, () -> mapper.readValue(
                "{\"unexpected\":true}", CapabilityPolicyConfigApiCommand.HeadersApiCommand.class));
    }

    @Test
    void shouldDetachEndpointCommandCollections() {
        List<SaveEndpointApiCommand.MatcherApiCommand> matchers = new ArrayList<>();
        matchers.add(new SaveEndpointApiCommand.MatcherApiCommand("GET", "/test"));
        Set<String> tags = new HashSet<>(Set.of("tag"));
        SaveEndpointApiCommand command = new SaveEndpointApiCommand(
                "r", "endpoint", "service", "route", matchers, tags, null, null, null, null);
        matchers.clear();
        tags.clear();
        assertEquals(1, command.matchers().size());
        assertEquals(Set.of("tag"), command.tags());
        assertThrows(UnsupportedOperationException.class, () -> command.matchers().clear());
        assertThrows(UnsupportedOperationException.class, () -> command.tags().clear());
        assertNull(command.requiredScopes());
        assertNull(command.requiredAudiences());
        SaveEndpointApiCommand nullable = new SaveEndpointApiCommand(
                null, null, null, null, null, null, null, null, null, null);
        assertNull(nullable.matchers());
        assertNull(nullable.tags());
    }

    @Test
    void shouldDetachAllDefinitionViewCollections() {
        Set<String> values = new HashSet<>(Set.of("value"));
        List<EndpointsApiView.MatcherApiView> matchers = new ArrayList<>(
                List.of(new EndpointsApiView.MatcherApiView("GET", "/test")));
        AppsApiView.AppApiView app = new AppsApiView.AppApiView("app", "WEB", null, values);
        ClientsApiView.ClientApiView client = new ClientsApiView.ClientApiView("client", null, values, null, null);
        EndpointsApiView.EndpointApiView endpoint = new EndpointsApiView.EndpointApiView(
                "endpoint", "service", "route", matchers, values, null, null, null, values, values);
        Set<AppsApiView.AppApiView> apps = new HashSet<>(Set.of(app));
        Set<ClientsApiView.ClientApiView> clients = new HashSet<>(Set.of(client));
        Set<EndpointsApiView.EndpointApiView> endpoints = new HashSet<>(Set.of(endpoint));
        AppsApiView appView = new AppsApiView("r", apps);
        ClientsApiView clientView = new ClientsApiView("r", clients);
        EndpointsApiView endpointView = new EndpointsApiView("r", endpoints);
        values.clear();
        matchers.clear();
        apps.clear();
        clients.clear();
        endpoints.clear();
        assertEquals(Set.of(app), appView.apps());
        assertEquals(Set.of(client), clientView.clients());
        assertEquals(Set.of(endpoint), endpointView.endpoints());
        for (Set<?> collection : List.of(appView.apps(), clientView.clients(), endpointView.endpoints(),
                app.allowedOrigins(), client.allowedAppIds(), endpoint.tags(),
                endpoint.requiredScopes(), endpoint.requiredAudiences())) {
            assertEquals(1, collection.size());
            assertThrows(UnsupportedOperationException.class, collection::clear);
        }
        assertEquals(1, endpoint.matchers().size());
        assertThrows(UnsupportedOperationException.class, () -> endpoint.matchers().clear());
        assertNull(new AppsApiView(null, null).apps());
        assertNull(new ClientsApiView(null, null).clients());
        assertNull(new EndpointsApiView(null, null).endpoints());
        assertNull(new AppsApiView.AppApiView(null, null, null, null).allowedOrigins());
        assertNull(new ClientsApiView.ClientApiView(null, null, null, null, null).allowedAppIds());
        EndpointsApiView.EndpointApiView nullable = new EndpointsApiView.EndpointApiView(
                null, null, null, null, null, null, null, null, null, null);
        assertNull(nullable.matchers());
        assertNull(nullable.tags());
        assertNull(nullable.requiredScopes());
        assertNull(nullable.requiredAudiences());
    }
}
