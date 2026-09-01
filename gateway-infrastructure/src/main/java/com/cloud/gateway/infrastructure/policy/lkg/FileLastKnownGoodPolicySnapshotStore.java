package com.cloud.gateway.infrastructure.policy.lkg;

import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public final class FileLastKnownGoodPolicySnapshotStore implements LastKnownGoodPolicySnapshotStore {
    private final Path location;

    @Override
    public Optional<String> load() {
        Path target = absoluteLocation();
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_TECH_POLICY_LAST_KNOWN_GOOD_FAILED, exception);
        }
    }

    @Override
    public void save(String content) {
        Path target = absoluteLocation();
        Path directory = target.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
            }
            moveAtomically(temporary, target);
        } catch (IOException exception) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_TECH_POLICY_LAST_KNOWN_GOOD_FAILED, exception);
        } finally {
            deleteTemporaryFile(temporary);
        }
    }

    private Path absoluteLocation() {
        return location.toAbsolutePath().normalize();
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTemporaryFile(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            log.warn("Failed to delete temporary last-known-good policy file path: {}", temporary, exception);
        }
    }
}
