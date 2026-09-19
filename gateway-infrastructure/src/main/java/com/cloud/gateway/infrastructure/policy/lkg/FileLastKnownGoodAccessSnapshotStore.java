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
public final class FileLastKnownGoodAccessSnapshotStore implements LastKnownGoodAccessSnapshotStore {
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
            writeFully(temporary, content);
            moveAtomically(temporary, target);
            // The rename is only durable once the directory entry itself is flushed.
            fsyncDirectory(directory);
        } catch (IOException exception) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_TECH_POLICY_LAST_KNOWN_GOOD_FAILED, exception);
        } finally {
            deleteTemporaryFile(temporary);
        }
    }

    /** A single {@code write} may persist fewer bytes than requested; loop until the buffer drains. */
    private static void writeFully(Path file, String content) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private Path absoluteLocation() {
        return location.toAbsolutePath().normalize();
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            // No atomic rename on this filesystem. A plain move may be a copy plus delete, so a
            // crash mid-way would leave a truncated target; copy first, force the target, and only
            // then drop the temporary file so the intact content survives for recovery.
            log.warn("Atomic move is unsupported; falling back to a forced copy path: {}", target);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        }
    }

    private static void fsyncDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException exception) {
            // Not every platform or filesystem allows flushing a directory; the file content is
            // already durable at this point, so this only weakens rename durability.
            log.debug("Directory flush is unsupported or failed path: {}", directory, exception);
        }
    }

    private static void deleteTemporaryFile(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            log.warn("Failed to delete temporary last-known-good access file path: {}", temporary, exception);
        }
    }
}
