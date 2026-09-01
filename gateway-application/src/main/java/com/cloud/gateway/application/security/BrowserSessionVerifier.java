package com.cloud.gateway.application.security;

import com.cloud.gateway.domain.access.AuthenticatedSubject;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface BrowserSessionVerifier {
    CompletionStage<Optional<AuthenticatedSubject>> verify(String sessionToken);
}
