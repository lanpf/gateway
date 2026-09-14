package com.cloud.gateway.infrastructure.persistence.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.cloud.gateway.application.access.publication.AccessSnapshotProvider;
import com.cloud.gateway.application.access.publication.AccessSnapshotPublisher;
import com.cloud.gateway.application.access.publication.VersionedAccessSnapshot;
import com.cloud.gateway.domain.access.IngressAccessSnapshot;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.cloud.gateway.infrastructure.policy.lkg.LastKnownGoodAccessSnapshotStore;
import com.cloud.gateway.infrastructure.policy.StoredAccessSnapshotCodec;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.operations.NacosConfigOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
@RequiredArgsConstructor
public final class NacosAccessSnapshotStore
        implements com.cloud.gateway.application.access.publication.AccessSnapshotProvider, com.cloud.gateway.application.access.publication.AccessSnapshotPublisher, AutoCloseable {
    private static final String SOURCE_REVISION_ALGORITHM = "MD5";

    private final NacosConfigOperations operations;
    private final NacosPolicyProperties properties;
    private final StoredAccessSnapshotCodec codec;
    private final LastKnownGoodAccessSnapshotStore lastKnownGood;
    private final AtomicReference<com.cloud.gateway.application.access.publication.VersionedAccessSnapshot> current = new AtomicReference<>();
    private final AtomicBoolean listening = new AtomicBoolean();
    private final Consumer<String> changeCallback = this::scheduleRemoteAccept;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gateway-nacos-policy-refresh");
        thread.setDaemon(true);
        return thread;
    });

    public void initialize() {
        try {
            connectAndRefresh();
        } catch (RuntimeException exception) {
            log.warn("Initial Nacos policy load failed; attempting last-known-good dataId={} group={}",
                    properties.getDataId(), properties.getGroup(), exception);
            loadLastKnownGood();
        }
        executor.scheduleWithFixedDelay(this::recoverSafely,
                properties.getRecoveryInterval().toMillis(),
                properties.getRecoveryInterval().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public com.cloud.gateway.application.access.publication.VersionedAccessSnapshot current() {
        com.cloud.gateway.application.access.publication.VersionedAccessSnapshot snapshot = current.get();
        if (snapshot == null) {
            throw new InfrastructureException(InfrastructureError.INFRA_TECH_POLICY_STORE_UNAVAILABLE);
        }
        return snapshot;
    }

    @Override
    public boolean compareAndSet(String expectedSourceRevision, IngressAccessSnapshot snapshot) {
        String content = codec.encodeSnapshot(snapshot);
        try {
            boolean published = operations.publishConfigCas(
                    properties.getDataId(), properties.getGroup(), content, expectedSourceRevision);
            if (published) {
                accept(content);
            }
            return published;
        } catch (NacosException exception) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
        }
    }

    @Override
    public void close() {
        operations.removeListener(properties.getDataId(), properties.getGroup(), changeCallback);
        listening.set(false);
        executor.shutdownNow();
    }

    private void connectAndRefresh() {
        try {
            String content;
            if (listening.compareAndSet(false, true)) {
                try {
                    content = operations.getConfigAndListen(properties.getDataId(), properties.getGroup(),
                            properties.getReadTimeout().toMillis(), changeCallback);
                } catch (NacosException exception) {
                    listening.set(false);
                    throw exception;
                }
            } else {
                content = operations.getConfig(properties.getDataId(), properties.getGroup(),
                        properties.getReadTimeout().toMillis());
            }
            if (!StringUtils.hasText(content)) {
                throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
            }
            accept(content);
        } catch (NacosException exception) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private void scheduleRemoteAccept(String content) {
        if (executor.isShutdown()) {
            return;
        }
        try {
            executor.execute(() -> acceptSafely(content));
        } catch (RejectedExecutionException exception) {
            if (!executor.isShutdown()) {
                throw exception;
            }
        }
    }

    private void acceptSafely(String content) {
        try {
            if (!StringUtils.hasText(content)) {
                throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
            }
            accept(content);
        } catch (RuntimeException exception) {
            log.error("Nacos policy notification failed dataId={} group={}",
                    properties.getDataId(), properties.getGroup(), exception);
        }
    }

    private void refreshSafely() {
        try {
            connectAndRefresh();
        } catch (RuntimeException exception) {
            log.error("Nacos policy refresh failed dataId={} group={}",
                    properties.getDataId(), properties.getGroup(), exception);
        }
    }

    private void recoverSafely() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        refreshSafely();
    }

    private void loadLastKnownGood() {
        String content = lastKnownGood.load().orElseThrow(() -> new InfrastructureException(
                InfrastructureError.INFRA_TECH_POLICY_STORE_UNAVAILABLE));
        accept(content);
    }

    private void accept(String content) {
        IngressAccessSnapshot candidate;
        try {
            candidate = codec.decodeSnapshot(content);
        } catch (RuntimeException exception) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID, exception);
        }
        String sourceRevision = sourceRevision(content);
        while (true) {
            com.cloud.gateway.application.access.publication.VersionedAccessSnapshot observed = current.get();
            com.cloud.gateway.application.access.publication.VersionedAccessSnapshot accepted = acceptedSnapshot(observed, candidate, sourceRevision);
            if (accepted == observed) {
                return;
            }
            if (current.compareAndSet(observed, accepted)) {
                saveLastKnownGood(content);
                log.info("Accepted gateway policy snapshot version={} contentHash={} sourceRevision={}",
                        accepted.snapshot().version(), accepted.snapshot().contentHash(), sourceRevision);
                return;
            }
        }
    }

    private void saveLastKnownGood(String content) {
        try {
            lastKnownGood.save(content);
        } catch (InfrastructureException exception) {
            log.error("Failed to persist last-known-good gateway policy path={}",
                    properties.getLastKnownGoodLocation(), exception);
        }
    }

    private com.cloud.gateway.application.access.publication.VersionedAccessSnapshot acceptedSnapshot(
            com.cloud.gateway.application.access.publication.VersionedAccessSnapshot observed,
            IngressAccessSnapshot candidate,
            String sourceRevision
    ) {
        if (observed == null) {
            return new com.cloud.gateway.application.access.publication.VersionedAccessSnapshot(candidate, sourceRevision);
        }
        if (observed.sourceRevision().equals(sourceRevision)) {
            return observed;
        }
        try {
            return switch (observed.snapshot().admissionOf(candidate)) {
                case STALE -> {
                    log.warn("Ignored stale Nacos policy snapshot dataId={} group={} currentVersion={} candidateVersion={}",
                            properties.getDataId(), properties.getGroup(), observed.snapshot().version(),
                            candidate.version());
                    yield observed;
                }
                case UNCHANGED -> new com.cloud.gateway.application.access.publication.VersionedAccessSnapshot(observed.snapshot(), sourceRevision);
                case CONFLICT -> throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
                case ADVANCE -> new com.cloud.gateway.application.access.publication.VersionedAccessSnapshot(candidate, sourceRevision);
            };
        } catch (DomainException exception) {
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID, exception);
        }
    }

    static String sourceRevision(String content) {
        try {
            byte[] digest = MessageDigest.getInstance(SOURCE_REVISION_ALGORITHM)
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_TECH_POLICY_SERIALIZATION_FAILED, exception);
        }
    }
}
