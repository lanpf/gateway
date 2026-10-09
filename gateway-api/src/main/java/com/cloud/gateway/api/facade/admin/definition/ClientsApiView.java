package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.framework.core.util.NullSafe;
import com.cloud.gateway.api.enums.ClientStatusApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;

import java.time.Instant;
import java.util.Set;

/**
 * Editing state of the client-policies definition, shared by access-settings and credential edits.
 * The sourceRevision is the shared CAS token of the whole client collection, not a per-client
 * version; a null revision means the definition is not initialized yet.
 */
public record ClientsApiView(String sourceRevision, Set<ClientApiView> clients) {
    public ClientsApiView {
        clients = clients == null ? null : NullSafe.copyOf(clients);
    }

    public record ClientApiView(
            String clientId,
            ClientStatusApiEnum status,
            Set<String> allowedAppIds,
            CredentialApiView activeCredential,
            CredentialApiView graceCredential
    ) {
        public ClientApiView {
            allowedAppIds = allowedAppIds == null ? null : NullSafe.copyOf(allowedAppIds);
        }
    }

    /** CredentialApiView facts for editing; secretRef is deliberately not echoed back. */
    public record CredentialApiView(
            String credentialId,
            SignatureContractApiEnum signatureContract,
            Instant validFrom,
            Instant expiresAt
    ) { }
}
