package com.cloud.gateway.infrastructure.security.spring.signature;

import com.cloud.gateway.application.security.BrowserSessionVerifier;
import com.cloud.gateway.domain.access.AuthenticatedSubject;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class UnavailableBrowserSessionVerifier implements BrowserSessionVerifier {
    @Override
    public CompletionStage<Optional<AuthenticatedSubject>> verify(String sessionToken) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
