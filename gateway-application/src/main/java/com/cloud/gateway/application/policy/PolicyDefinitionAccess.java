package com.cloud.gateway.application.policy;

import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.domain.access.IngressAccessContent;
import java.util.Map;
import java.util.function.BiPredicate;
import lombok.RequiredArgsConstructor;

/** Typed full-definition access shared by publication and recovery. */
@RequiredArgsConstructor
public final class PolicyDefinitionAccess {
    private final WritableEndpointRegistryDefinitionRepository endpoints;
    private final WritableAppPolicyDefinitionRepository apps;
    private final WritableClientPolicyDefinitionRepository clients;
    private final WritableEndpointAccessPolicyDefinitionRepository endpointPolicies;
    private final WritablePolicyCompositionDefinitionRepository policies;

    PolicyDefinitions load() {
        return new PolicyDefinitionReader(endpoints, apps, clients, endpointPolicies, policies).load();
    }

    void restore(PolicyDefinitions observed, IngressAccessContent target) {
        restore(endpoints, observed.endpoints(), target.endpointRegistry(),
                (left, right) -> left.definitions().equals(right.definitions()));
        restore(apps, observed.apps(), target.appPolicies(), Map::equals);
        restore(clients, observed.clients(), target.clients(), Map::equals);
        restore(endpointPolicies, observed.endpointPolicies(), target.endpointPolicies(), Map::equals);
        restore(policies, observed.policies(), target.policies(), Object::equals);
    }

    private <T> void restore(WritablePolicyDefinitionRepository<T> repository,
            VersionedPolicyDefinition<T> observed, T target, BiPredicate<T, T> equality) {
        if (!equality.test(observed.value(), target)) {
            if (!repository.compareAndSet(observed.sourceRevision(), target)) {
                throw new ApplicationException(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT);
            }
        }
    }
}
