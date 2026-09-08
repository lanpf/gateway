package com.cloud.gateway.infrastructure.policy.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloud.gateway.application.policy.VersionedPolicyDefinition;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.ClientAppStatus;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.infrastructure.policy.StoredPolicyDefinitionCodec;
import com.cloud.gateway.infrastructure.policy.StoredPolicySnapshotMapper;
import com.cloud.gateway.infrastructure.policy.config.IngressPolicyDefinitionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PropertiesPolicyDefinitionRepositoriesTest {
    @Test
    void shouldLoadClientAppsWithoutConvertingUnrelatedDefinitions() {
        IngressPolicyDefinitionProperties properties = new IngressPolicyDefinitionProperties();
        IngressPolicyDefinitionProperties.ClientApp clientApp = new IngressPolicyDefinitionProperties.ClientApp();
        clientApp.setStatus(ClientAppStatus.ENABLED);
        clientApp.setPlatform("WEB");
        clientApp.getAllowedBrowserOrigins().add("https://browser.example.com");
        properties.getClientApps().put("web-app", clientApp);
        properties.getChannelCredentials().put(
                "unrelated-invalid-credential", new IngressPolicyDefinitionProperties.ChannelCredential());
        StoredPolicySnapshotMapper mapper = new StoredPolicySnapshotMapper();
        StoredPolicyDefinitionCodec codec = new StoredPolicyDefinitionCodec(
                mapper, new ObjectMapper().findAndRegisterModules());
        PropertiesClientAppPolicyDefinitionRepository repository =
                new PropertiesClientAppPolicyDefinitionRepository(properties, mapper, codec);

        VersionedPolicyDefinition<Map<ClientAppId, IngressClientAppPolicy>> observed = repository.load();

        assertEquals("WEB", observed.value().get(new ClientAppId("web-app")).platform());
        assertEquals(Set.of("https://browser.example.com"),
                observed.value().get(new ClientAppId("web-app")).allowedBrowserOrigins());
    }
}
