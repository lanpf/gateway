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
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.FunctionCounter;
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
    private final AtomicBoolean degraded = new AtomicBoolean();
    private final AtomicBoolean lkgDegraded = new AtomicBoolean();
    private final AtomicLong refreshFailures = new AtomicLong();
    private final AtomicLong lkgFailures = new AtomicLong();
    private final AtomicLong staleNotifications = new AtomicLong();
    private final AtomicLong lastSuccessfulRefreshMillis = new AtomicLong();
    private final AtomicLong failuresSinceReport = new AtomicLong();
    private final AtomicLong lastFailureReportNanos = new AtomicLong();
    private final AtomicReference<com.cloud.gateway.domain.ingress.snapshot.SnapshotVersion> staleReportedVersion = new AtomicReference<>();
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
            reportRefreshFailure("initialize", exception);
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
                    PersistenceNacosError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
        }
        if (!published) {
            return false;
        }
        // The remote write already happened, so a failure to project it locally must not be reported
        // as a failed publication: the caller would retry and publish a second time. The Nacos change
        // notification and the periodic recovery loop both re-attempt the local accept.
        try {
            accept(content);
            reportRecovered();
        } catch (RuntimeException exception) {
            reportRefreshFailure("publication_projection", exception);
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
            reportRecovered();
        } catch (NacosException exception) {
            throw new InfrastructureException(
                    PersistenceNacosError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
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
            reportRecovered();
        } catch (RuntimeException exception) {
            reportRefreshFailure("notification", exception);
        }
    }

    private void refreshSafely() {
        try {
            connectAndRefresh();
        } catch (RuntimeException exception) {
            reportRefreshFailure("refresh", exception);
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
                if (lkgDegraded.get() && candidate.version().equals(observed.snapshot().version())
                        && candidate.contentHash().equals(observed.snapshot().contentHash())) {
                    saveLastKnownGood(content);
                }
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
            if (lkgDegraded.compareAndSet(true, false)) {
                log.info("Gateway last-known-good persistence recovered failures={}", lkgFailures.get());
            }
        } catch (InfrastructureException exception) {
            lkgFailures.incrementAndGet();
            if (lkgDegraded.compareAndSet(false, true)) {
                log.warn("Gateway last-known-good persistence degraded exceptionType={}", exception.getClass().getSimpleName());
            }
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
                staleNotifications.incrementAndGet();
                var version = observed.snapshot().version();
                var previous = staleReportedVersion.getAndSet(version);
                if (!version.equals(previous)) {
                    log.warn("Ignored stale Nacos access snapshot currentVersion={} candidateVersion={} total={}",
                            version.value(), candidate.version().value(), staleNotifications.get());
                }
                yield observed;
            }
            case UNCHANGED -> new RevisionedAccessSnapshot(observed.snapshot(), sourceRevision);
            case CONFLICT -> throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
            case ADVANCE -> new RevisionedAccessSnapshot(candidate, sourceRevision);
        };
    }

    public void bindMetrics(MeterRegistry registry) {
        com.cloud.framework.core.validation.Require.notNull(registry);
        FunctionCounter.builder("gateway.access.refresh.failures", refreshFailures, AtomicLong::doubleValue).register(registry);
        FunctionCounter.builder("gateway.access.lkg.failures", lkgFailures, AtomicLong::doubleValue).register(registry);
        FunctionCounter.builder("gateway.access.stale_notifications", staleNotifications, AtomicLong::doubleValue).register(registry);
        Gauge.builder("gateway.access.refresh.last_success_seconds", lastSuccessfulRefreshMillis,
                value -> value.get() / 1000.0).register(registry);
        Gauge.builder("gateway.access.refresh.degraded", degraded, value -> value.get() ? 1 : 0).register(registry);
        Gauge.builder("gateway.access.lkg.degraded", lkgDegraded, value -> value.get() ? 1 : 0).register(registry);
    }

    private void reportRefreshFailure(String operation, RuntimeException exception) {
        refreshFailures.incrementAndGet();
        failuresSinceReport.incrementAndGet();
        long now = System.nanoTime();
        long previous = lastFailureReportNanos.get();
        boolean transition = degraded.compareAndSet(false, true);
        boolean window = now - previous >= TimeUnit.MINUTES.toNanos(1)
                && lastFailureReportNanos.compareAndSet(previous, now);
        if (transition || window) {
            lastFailureReportNanos.set(now);
            log.warn("Gateway access refresh degraded operation={} failures={} lastSuccessMillis={} exceptionType={}",
                    operation, failuresSinceReport.getAndSet(0), lastSuccessfulRefreshMillis.get(),
                    exception.getClass().getSimpleName());
        }
    }

    private void reportRecovered() {
        lastSuccessfulRefreshMillis.set(System.currentTimeMillis());
        if (degraded.compareAndSet(true, false)) {
            log.info("Gateway access refresh recovered failures={} total={}",
                    failuresSinceReport.getAndSet(0), refreshFailures.get());
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
