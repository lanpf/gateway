package com.cloud.gateway.domain.policy.client;

import com.cloud.gateway.domain.client.Credential;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.client.ClientPolicy;
import com.cloud.gateway.domain.client.ClientStatus;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.SignatureContract;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.app.AppId;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientPolicyTest {
    private static final ClientId CLIENT = new ClientId("client-a");
    private static final AppId APP = new AppId("app-a");

    @Test void shouldAllowOnlyConfiguredAppsWhileEnabledWithoutRequiringCredentials() {
        var policy = new ClientPolicy(CLIENT, Set.of(APP), null, ClientStatus.ENABLED);
        assertTrue(policy.allows(APP));
        assertFalse(policy.allows(new AppId("other")));
        assertFalse(policy.withAllowedApps(Set.of(APP), ClientStatus.DISABLED).allows(APP));
        assertNull(policy.credentials());
    }

    @Test void shouldDefensivelyCopyAllowedApps() {
        HashSet<AppId> apps = new HashSet<>(Set.of(APP));
        var policy = new ClientPolicy(CLIENT, apps);
        apps.clear();
        assertEquals(Set.of(APP), policy.allowedApps());
        assertThrows(UnsupportedOperationException.class, () -> policy.allowedApps().clear());
    }

    @Test void shouldPreserveCredentialsWhenChangingAccess() {
        Credentials credentials = credentials(CLIENT);
        var policy = new ClientPolicy(CLIENT, Set.of(APP), credentials, ClientStatus.ENABLED);
        ClientPolicy updated = policy.withAllowedApps(Set.of(new AppId("other")), ClientStatus.DISABLED);
        assertEquals(credentials, updated.credentials());
        assertEquals(ClientStatus.DISABLED, updated.status());
        assertEquals(Set.of(new AppId("other")), updated.allowedApps());
        assertEquals(Set.of(APP), policy.allowedApps());
        assertEquals(ClientStatus.ENABLED, policy.status());
    }

    @Test void shouldPreserveAccessWhenReplacingOrClearingCredentials() {
        var policy = new ClientPolicy(CLIENT, Set.of(APP), null, ClientStatus.DISABLED);
        ClientPolicy updated = policy.withCredentials(credentials(CLIENT));
        assertEquals(policy.allowedApps(), updated.allowedApps());
        assertEquals(policy.status(), updated.status());
        assertNotNull(updated.credentials());
        assertNull(updated.withCredentials(null).credentials());
        assertNull(policy.credentials());
    }

    @Test void shouldRejectCredentialOwnershipMismatchAndMissingRequiredFields() {
        assertThrows(DomainException.class, () -> new ClientPolicy(CLIENT, Set.of(APP), credentials(new ClientId("other")), ClientStatus.ENABLED));
        assertThrows(DomainException.class, () -> new ClientPolicy(null, Set.of(APP), null, ClientStatus.ENABLED));
        assertThrows(DomainException.class, () -> new ClientPolicy(CLIENT, null, null, ClientStatus.ENABLED));
        assertThrows(DomainException.class, () -> new ClientPolicy(CLIENT, Set.of(APP), null, null));
    }

    private static Credentials credentials(ClientId client) {
        return new Credentials(new Credential(new CredentialId("active"), client,
                SignatureContract.HTTP_REQUEST_SIGNATURE_V1, "secret-reference", Instant.EPOCH,
                Instant.parse("2099-01-01T00:00:00Z")), null);
    }
}
