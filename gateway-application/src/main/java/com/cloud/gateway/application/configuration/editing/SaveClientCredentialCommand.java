package com.cloud.gateway.application.configuration.editing;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.ClientId;

public record SaveClientCredentialCommand(Revision expectedSourceRevision,
                                          ClientId clientId, Credentials credentials) { }
