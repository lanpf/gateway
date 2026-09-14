package com.cloud.gateway.api.facade.admin.definition;

import com.cloud.gateway.api.enums.ClientStatusApiEnum;
import com.cloud.gateway.api.enums.SignatureContractApiEnum;

import java.time.Instant;
import java.util.Set;

/**
 * Editing state of the client-policies definition, shared by access-settings and credential edits.
 * The sourceRevision is the shared CAS token of the whole client collection, not a per-client
 * version; a null revision means the definition is not initialized yet.
 */
public record ClientsApiView(String sourceRevision, Set<Client> clients) {

    public record Client(
            String clientId,
            ClientStatusApiEnum status,
            Set<String> allowedAppIds,
            Credential activeCredential,
            Credential graceCredential
    ) { }

    /** Credential facts for editing; secretRef is deliberately not echoed back. */
    public record Credential(
            String credentialId,
            SignatureContractApiEnum signatureContract,
            Instant validFrom,
            Instant expiresAt
    ) { }
}
