package com.cloud.gateway.application.security;

public interface SecretResolver {
    String resolve(String secretRef);
}
