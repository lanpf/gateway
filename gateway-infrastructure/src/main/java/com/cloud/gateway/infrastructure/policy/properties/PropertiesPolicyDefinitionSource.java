package com.cloud.gateway.infrastructure.policy.properties;

import com.cloud.gateway.application.access.definition.VersionedAccessDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.function.Supplier;

final class PropertiesPolicyDefinitionSource<T> {
    private final Supplier<T> loader;
    private final Function<T, String> encoder;

    PropertiesPolicyDefinitionSource(Supplier<T> loader, Function<T, String> encoder) {
        this.loader = loader;
        this.encoder = encoder;
    }

    com.cloud.gateway.application.access.definition.VersionedAccessDefinition<T> load() {
        T value = loader.get();
        return new com.cloud.gateway.application.access.definition.VersionedAccessDefinition<>(value, sourceRevision(encoder.apply(value)));
    }

    private static String sourceRevision(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
