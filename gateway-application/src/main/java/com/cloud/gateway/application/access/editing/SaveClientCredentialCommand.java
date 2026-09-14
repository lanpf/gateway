package com.cloud.gateway.application.access.editing;

import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.ClientId;

public record SaveClientCredentialCommand(String expectedSourceRevision, ClientId clientId,
                                              Credentials credentials) { }
