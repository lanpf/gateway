package com.cloud.gateway.application.access.publication;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.access.IngressAccessContent;
import java.util.function.BiPredicate;
import lombok.RequiredArgsConstructor;

/** Typed full-definition access shared by publication and recovery. */
@RequiredArgsConstructor
public final class AccessDefinitions {
    private final com.cloud.gateway.application.access.definition.WritableEndpointAccessDefinitionRepository endpoints;
    private final com.cloud.gateway.application.access.definition.WritableAppAccessDefinitionRepository apps;
    private final com.cloud.gateway.application.access.definition.WritableClientAccessDefinitionRepository clients;
    private final com.cloud.gateway.application.access.definition.WritablePolicyConfigDefinitionRepository policies;

    com.cloud.gateway.application.access.definition.ObservedAccessDefinitions load() {
        return new com.cloud.gateway.application.access.definition.AccessDefinitionReader(endpoints, apps, clients, policies).load();
    }

    void restore(com.cloud.gateway.application.access.definition.ObservedAccessDefinitions observed, IngressAccessContent target) {
        restore(endpoints, observed.endpoints(), target.endpointRegistry(),
                (left, right) -> left.asMap().equals(right.asMap()));
        restore(apps, observed.apps(), target.appRegistry(),
                (left, right) -> left.asMap().equals(right.asMap()));
        restore(clients, observed.clients(), target.clientRegistry(),
                (left, right) -> left.asMap().equals(right.asMap()));
        restore(policies, observed.policies(), target.policies(), Object::equals);
    }

    private <T> void restore(com.cloud.gateway.application.access.definition.WritableAccessDefinitionRepository<T> repository,
            com.cloud.gateway.application.access.definition.VersionedAccessDefinition<T> observed, T target, BiPredicate<T, T> equality) {
        if (!equality.test(observed.value(), target)) {
            String nextRevision = repository.compareAndSet(observed.sourceRevision(), target);
            if (nextRevision == null) {
                throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
            }
        }
    }
}
