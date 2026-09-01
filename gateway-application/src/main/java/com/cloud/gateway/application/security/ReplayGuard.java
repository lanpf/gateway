package com.cloud.gateway.application.security;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

public interface ReplayGuard {
    CompletionStage<Boolean> register(String evidenceKey, Instant expiresAt);
}
