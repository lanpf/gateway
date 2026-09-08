package com.cloud.gateway.application.security;

import java.util.concurrent.CompletionStage;

public interface BrowserSessionVerifier {
    CompletionStage<BrowserSessionVerificationOutcome> verify(String sessionToken);
}
