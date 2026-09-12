package com.cloud.gateway.application.policy;

import com.cloud.gateway.domain.client.Credentials;
import com.cloud.gateway.domain.client.ClientId;

public record SaveClientCredentialPolicyCommand(String expectedSourceRevision, ClientId clientId,
                                              Credentials credentials) { }
