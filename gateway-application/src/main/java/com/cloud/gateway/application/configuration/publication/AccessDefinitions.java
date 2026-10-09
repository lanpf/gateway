package com.cloud.gateway.application.configuration.publication;

import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.application.configuration.definition.ObservedAccessDefinitions;
import com.cloud.gateway.application.configuration.definition.RevisionedAccessDefinition;
import com.cloud.gateway.application.configuration.definition.WritableAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableAppAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableClientAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableEndpointAccessDefinitionRepository;
import com.cloud.gateway.application.configuration.definition.WritableCapabilityPolicyConfigDefinitionRepository;
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
    private final WritableCapabilityPolicyConfigDefinitionRepository policies;

    ObservedAccessDefinitions load(AccessOperationBudget.Deadline deadline) {
        deadline.check();
        var endpointValue = endpoints.load();
        deadline.check();
        var appValue = apps.load();
        deadline.check();
        var clientValue = clients.load();
        deadline.check();
        var policyValue = policies.load();
        deadline.check();
        return new ObservedAccessDefinitions(endpointValue, appValue, clientValue, policyValue);
    }

    void restore(ObservedAccessDefinitions observed, IngressContent target, AccessOperationBudget.Deadline deadline) {
        restore(endpoints, observed.endpoints(), target.endpoints(), deadline,
                (left, right) -> left.asMap().keySet().equals(right.asMap().keySet())
                        && left.values().stream().allMatch(value -> value.sameDefinition(right.asMap().get(value.getId()))));
        restore(apps, observed.apps(), target.apps(), deadline,
                (left, right) -> left.asMap().keySet().equals(right.asMap().keySet())
                        && left.values().stream().allMatch(value -> value.sameDefinition(right.asMap().get(value.getId()))));
        restore(clients, observed.clients(), target.clients(), deadline,
                (left, right) -> left.asMap().keySet().equals(right.asMap().keySet())
                        && left.values().stream().allMatch(value -> value.sameDefinition(right.asMap().get(value.getId()))));
        restore(policies, observed.policies(), target.policyConfig(), deadline, Object::equals);
    }

    private <T> void restore(WritableAccessDefinitionRepository<T> repository,
                             RevisionedAccessDefinition<T> observed, T target, AccessOperationBudget.Deadline deadline, BiPredicate<T, T> equality) {
        deadline.check();
        if (!equality.test(observed.value(), target)) {
            Revision nextRevision = repository.compareAndSet(observed.sourceRevision(), target);
            if (nextRevision == null) {
                throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
            }
        }
        deadline.definitionCompleted();
        deadline.check();
    }
}
