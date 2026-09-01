package com.cloud.gateway.application.policy;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.endpoint.EndpointCatalog;
import com.cloud.gateway.domain.endpoint.EndpointId;
import com.cloud.gateway.domain.policy.ChannelCode;
import com.cloud.gateway.domain.policy.ClientAppId;
import com.cloud.gateway.domain.policy.IngressChannelAccessPolicy;
import com.cloud.gateway.domain.policy.IngressChannelCredentialPolicy;
import com.cloud.gateway.domain.policy.IngressClientAppPolicy;
import com.cloud.gateway.domain.policy.IngressPolicyContent;
import com.cloud.gateway.domain.policy.IngressPolicySnapshot;
import com.cloud.gateway.domain.policy.IngressRouteAccessPolicy;
import java.time.Clock;
import java.util.Map;
import java.util.function.BiPredicate;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PolicySnapshotCommandService {
    private final WritableEndpointCatalogDefinitionRepository endpointCatalogRepository;
    private final WritableClientAppPolicyDefinitionRepository clientAppRepository;
    private final WritableChannelAccessPolicyDefinitionRepository channelAccessRepository;
    private final WritableChannelCredentialPolicyDefinitionRepository channelCredentialRepository;
    private final WritableRouteAccessPolicyDefinitionRepository routeAccessRepository;
    private final PolicySnapshotProvider provider;
    private final PolicySnapshotPublisher publisher;
    private final PolicySnapshotHistory history;
    private final PolicyRollbackState rollbackState;
    private final PolicySnapshotHasher hasher;
    private final PolicyPublishLock publishLock;
    private final PolicyRollbackLock rollbackLock;
    private final Clock clock;

    public PolicyPublicationOutput publish(PublishPolicySnapshotCommand command) {
        return publishLock.execute(this::publishDefinitions);
    }

    public PolicyPublicationOutput rollback(RollbackPolicySnapshotCommand command) {
        rollbackState.activate();
        return rollbackLock.execute(() -> publishLock.execute(
                () -> rollbackLocked(command.targetSnapshotVersion())));
    }

    private PolicyPublicationOutput publishDefinitions() {
        verifyNormalPublicationAllowed();
        PolicyDefinitions observedDefinitions = loadDefinitions();
        VersionedPolicySnapshot observedSnapshot = provider.current();
        IngressPolicyContent content = observedDefinitions.toContent();
        String contentHash = hasher.hash(content);
        if (contentHash.equals(observedSnapshot.snapshot().contentHash())) {
            throw new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_NO_CHANGES);
        }
        PolicyDefinitions verifiedDefinitions = loadDefinitions();
        if (!observedDefinitions.revisions().equals(verifiedDefinitions.revisions())) {
            throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITIONS_CHANGED);
        }
        return publish(observedSnapshot, content, contentHash);
    }

    private PolicyPublicationOutput rollbackLocked(long targetSnapshotVersion) {
        IngressPolicySnapshot target = history.find(targetSnapshotVersion)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationError.APP_POLICY_SNAPSHOT_HISTORY_NOT_FOUND));
        String targetHash = hasher.hash(target.content());
        restoreDefinitions(loadDefinitions(), target.content());
        IngressPolicyContent restoredContent = loadDefinitions().toContent();
        if (!targetHash.equals(hasher.hash(restoredContent))) {
            throw definitionConflict();
        }

        VersionedPolicySnapshot observedSnapshot = provider.current();
        PolicyPublicationOutput output = targetHash.equals(observedSnapshot.snapshot().contentHash())
                ? new PolicyPublicationOutput(observedSnapshot.snapshot().snapshotVersion(), targetHash)
                : publish(observedSnapshot, restoredContent, targetHash);
        rollbackState.clear();
        return output;
    }

    private PolicyPublicationOutput publish(
            VersionedPolicySnapshot observed,
            IngressPolicyContent content,
            String contentHash
    ) {
        IngressPolicySnapshot current = observed.snapshot();
        IngressPolicySnapshot next = new IngressPolicySnapshot(
                current.snapshotVersion() + 1, content, contentHash, clock.instant());
        if (!publisher.compareAndSet(observed.sourceRevision(), next)) {
            throw new ApplicationException(ApplicationError.APP_POLICY_SNAPSHOT_PUBLISH_CONFLICT);
        }
        return new PolicyPublicationOutput(next.snapshotVersion(), next.contentHash());
    }

    private void verifyNormalPublicationAllowed() {
        if (rollbackState.isActive()) {
            throw new ApplicationException(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS);
        }
    }

    private PolicyDefinitions loadDefinitions() {
        return new PolicyDefinitions(
                endpointCatalogRepository.load(),
                clientAppRepository.load(),
                channelAccessRepository.load(),
                channelCredentialRepository.load(),
                routeAccessRepository.load());
    }

    private void restoreDefinitions(PolicyDefinitions observed, IngressPolicyContent target) {
        restore(endpointCatalogRepository, observed.endpointCatalog(), target.endpointCatalog(),
                (left, right) -> left.asMap().equals(right.asMap()));
        restore(clientAppRepository, observed.clientApps(), target.clientAppPolicies(), Map::equals);
        restore(channelAccessRepository, observed.channelAccess(), target.channelAccessPolicies(), Map::equals);
        restore(channelCredentialRepository, observed.channelCredentials(),
                target.channelCredentialPolicies(), Map::equals);
        restore(routeAccessRepository, observed.routeAccess(), target.routeAccessPolicies(), Map::equals);
    }

    private <T> void restore(
            WritablePolicyDefinitionRepository<T> repository,
            VersionedPolicyDefinition<T> observed,
            T target,
            BiPredicate<T, T> equality
    ) {
        if (!equality.test(observed.value(), target)
                && !repository.compareAndSet(observed.sourceRevision(), target)) {
            throw definitionConflict();
        }
    }

    private static ApplicationException definitionConflict() {
        return new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
    }

    private record PolicyDefinitions(
            VersionedPolicyDefinition<EndpointCatalog> endpointCatalog,
            VersionedPolicyDefinition<Map<ClientAppId, IngressClientAppPolicy>> clientApps,
            VersionedPolicyDefinition<Map<ChannelCode, IngressChannelAccessPolicy>> channelAccess,
            VersionedPolicyDefinition<Map<ChannelCode, IngressChannelCredentialPolicy>> channelCredentials,
            VersionedPolicyDefinition<Map<EndpointId, IngressRouteAccessPolicy>> routeAccess
    ) {
        private IngressPolicyContent toContent() {
            return new IngressPolicyContent(endpointCatalog.value(), clientApps.value(), channelAccess.value(),
                    channelCredentials.value(), routeAccess.value());
        }

        private DefinitionRevisions revisions() {
            return new DefinitionRevisions(endpointCatalog.sourceRevision(), clientApps.sourceRevision(),
                    channelAccess.sourceRevision(), channelCredentials.sourceRevision(), routeAccess.sourceRevision());
        }
    }

    private record DefinitionRevisions(
            String endpointCatalog,
            String clientApps,
            String channelAccess,
            String channelCredentials,
            String routeAccess
    ) {
    }
}
