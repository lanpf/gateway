package com.cloud.gateway.infrastructure.persistence.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotCapturer;
import com.cloud.gateway.application.configuration.publication.AccessSnapshotPublisher;
import com.cloud.gateway.application.configuration.publication.RevisionedAccessSnapshot;
import com.cloud.gateway.domain.ingress.snapshot.IngressSnapshot;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
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
        implements AccessSnapshotCapturer, AccessSnapshotPublisher, AutoCloseable {
    private static final String SOURCE_REVISION_ALGORITHM = "MD5";

    private final NacosConfigOperations operations;
    private final NacosPolicyProperties properties;
    private final StoredAccessSnapshotCodec codec;
    private final LastKnownGoodAccessSnapshotStore lastKnownGood;
    private final AtomicReference<RevisionedAccessSnapshot> current = new AtomicReference<>();
    private final AtomicBoolean listening = new AtomicBoolean();
    private final Consumer<String> changeCallback = this::scheduleRemoteAccept;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gateway-nacos-access-refresh");
        thread.setDaemon(true);
        return thread;
    });

    public void initialize() {
        try {
            connectAndRefresh();
        } catch (RuntimeException exception) {
            log.warn("Initial Nacos access load failed; attempting last-known-good dataId={} group={}",
                    properties.getDataId(), properties.getGroup(), exception);
            loadLastKnownGood();
        }
        executor.scheduleWithFixedDelay(this::recoverSafely,
                properties.getRecoveryInterval().toMillis(),
                properties.getRecoveryInterval().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public RevisionedAccessSnapshot capture() {
        RevisionedAccessSnapshot snapshot = current.get();
        if (snapshot == null) {
            throw new InfrastructureException(InfrastructureError.INFRA_TECH_POLICY_STORE_UNAVAILABLE);
        }
        return snapshot;
    }

    @Override
    public boolean compareAndSet(Revision expectedSourceRevision, IngressSnapshot snapshot) {
        String content = codec.encodeSnapshot(snapshot);
        boolean published;
        try {
            published = operations.publishConfigCas(
                    properties.getDataId(), properties.getGroup(), content, expectedSourceRevision.value());
        } catch (NacosException exception) {
            throw new InfrastructureException(
                    InfrastructureError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
        }
        if (!published) {
            return false;
        }
        // The remote write already happened, so a failure to project it locally must not be reported
        // as a failed publication: the caller would retry and publish a second time. The Nacos change
        // notification and the periodic recovery loop both re-attempt the local accept.
        try {
            accept(content);
        } catch (RuntimeException exception) {
            log.error("Published access snapshot could not be projected locally dataId={} group={}",
                    properties.getDataId(), properties.getGroup(), exception);
        }
        return true;
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
            log.error("Nacos access notification failed dataId={} group={}",
                    properties.getDataId(), properties.getGroup(), exception);
        }
    }

    private void refreshSafely() {
        try {
            connectAndRefresh();
        } catch (RuntimeException exception) {
            log.error("Nacos access refresh failed dataId={} group={}",
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
        IngressSnapshot candidate;
        try {
            candidate = codec.decodeSnapshot(content);
        } catch (RuntimeException exception) {
            // The codec reports a generic deserialization failure; this adapter knows the content
            // came from Nacos, so it names the real cause. The domain reason stays as the cause.
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID, exception);
        }
        Revision sourceRevision = new Revision(sourceRevision(content));
        while (true) {
            RevisionedAccessSnapshot observed = current.get();
            RevisionedAccessSnapshot accepted = acceptedSnapshot(observed, candidate, sourceRevision);
            if (accepted == observed) {
                return;
            }
            if (current.compareAndSet(observed, accepted)) {
                saveLastKnownGood(content);
                log.info("Accepted gateway access snapshot version={} contentHash={} sourceRevision={}",
                        accepted.snapshot().version().value(), accepted.snapshot().contentHash(), sourceRevision);
                return;
            }
        }
    }

    private void saveLastKnownGood(String content) {
        try {
            lastKnownGood.save(content);
        } catch (InfrastructureException exception) {
            log.error("Failed to persist last-known-good gateway access path={}",
                    properties.getLastKnownGoodLocation(), exception);
        }
    }

    private RevisionedAccessSnapshot acceptedSnapshot(
            RevisionedAccessSnapshot observed,
            IngressSnapshot candidate,
            Revision sourceRevision
    ) {
        if (observed == null) {
            return new RevisionedAccessSnapshot(candidate, sourceRevision);
        }
        if (observed.sourceRevision().equals(sourceRevision)) {
            return observed;
        }
        return switch (observed.snapshot().acceptanceOf(candidate)) {
            case STALE -> {
                log.warn("Ignored stale Nacos access snapshot dataId={} group={} currentVersion={} candidateVersion={}",
                        properties.getDataId(), properties.getGroup(), observed.snapshot().version().value(),
                        candidate.version().value());
                yield observed;
            }
            case UNCHANGED -> new RevisionedAccessSnapshot(observed.snapshot(), sourceRevision);
            case CONFLICT -> throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
            case ADVANCE -> new RevisionedAccessSnapshot(candidate, sourceRevision);
        };
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
