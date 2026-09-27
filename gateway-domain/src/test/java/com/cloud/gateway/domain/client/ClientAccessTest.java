package com.cloud.gateway.domain.client;

import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.Client;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientAccess;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.common.DomainException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAccessTest {
    private static final AppId APP = new AppId("app-a");

    @Test
    void shouldAllowOnlyConfiguredAppsThroughEnabledClient() {
        Client client = client(ClientStatus.ENABLED);

        assertTrue(client.allowsApp(APP));
        assertFalse(client.allowsApp(new AppId("other")));
        Client disabled = new Client(client.clientId(), ClientStatus.DISABLED, client.access());
        assertFalse(disabled.allowsApp(APP));
    }

    @Test
    void shouldDefensivelyCopyAllowedApps() {
        HashSet<AppId> apps = new HashSet<>(Set.of(APP));
        ClientAccess policy = new ClientAccess(apps, null);
        apps.clear();

        assertEquals(Set.of(APP), policy.allowedApps());
        assertThrows(UnsupportedOperationException.class, () -> policy.allowedApps().clear());
    }

    @Test
    void shouldTreatMissingAppsAndCredentialsAsEmptyAndAbsent() {
        ClientAccess policy = new ClientAccess(null, null);

        assertEquals(Set.of(), policy.allowedApps());
        assertNull(policy.credentials());
    }

    @Test
    void shouldRejectNullAppElements() {
        assertThrows(DomainException.class, () -> new ClientAccess(Collections.singleton(null), null));
    }

    private static Client client(ClientStatus status) {
        return new Client(new ClientId("client-a"), status,
                new ClientAccess(Set.of(APP), credentials()));
    }

    private static Credentials credentials() {
        return new Credentials(new Credential(new CredentialId("active"), "secret-ref",
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, Instant.EPOCH,
                Instant.parse("2099-01-01T00:00:00Z")), null);
    }
}
