package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.application.configuration.definition.AccessDefinitionReader;
import com.cloud.gateway.application.configuration.definition.ObservedAccessDefinitions;
import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.application.configuration.definition.WritableAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableAppAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableClientAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableEndpointAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableCapabilityCompositionDefinitionRepository;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.ingress.snapshot.IngressContent;
import java.util.function.BiPredicate;
import lombok.RequiredArgsConstructor;

/** Typed full-definition access shared by publication and recovery. */
@RequiredArgsConstructor
public final class AccessDefinitions {
    private final WritableEndpointAccessDefinitionRepository endpoints;
    private final WritableAppAccessDefinitionRepository apps;
    private final WritableClientAccessDefinitionRepository clients;
    private final WritableCapabilityCompositionDefinitionRepository policies;

    ObservedAccessDefinitions load() {
        return new AccessDefinitionReader(endpoints, apps, clients, policies).load();
    }

    void restore(ObservedAccessDefinitions observed, IngressContent target) {
        restore(endpoints, observed.endpoints(), target.endpointRegistry(),
                (left, right) -> left.asMap().equals(right.asMap()));
        restore(apps, observed.apps(), target.appRegistry(),
                (left, right) -> left.asMap().equals(right.asMap()));
        restore(clients, observed.clients(), target.clientRegistry(),
                (left, right) -> left.asMap().equals(right.asMap()));
        restore(policies, observed.policies(), target.capabilityComposition(), Object::equals);
    }

    private <T> void restore(WritableAccessDefinitionRepository<T> repository,
                             RevisionedAccessDefinition<T> observed, T target, BiPredicate<T, T> equality) {
        if (!equality.test(observed.value(), target)) {
            Revision nextRevision = repository.compareAndSet(observed.sourceRevision(), target);
            if (nextRevision == null) {
                throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
            }
        }
    }
}
