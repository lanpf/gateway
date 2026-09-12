package com.cloud.gateway.interfaces.facade;

import com.cloud.gateway.api.facade.IngressAccessCommandFacade;
import com.cloud.gateway.api.policy.ReleaseSnapshotApiCommand;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IngressAccessCommandFacadeValidationTest {
    // Validate the actual implementation's inherited contract without invoking any collaborators.
    private final DefaultIngressAccessCommandFacade facade = new DefaultIngressAccessCommandFacade(
            null, null, null, null, null, null, null, null);

    @Test
    void shouldRejectNullCommandsOnEveryFacadeOperationThroughInheritedConstraints() throws Exception {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            for (Method contract : IngressAccessCommandFacade.class.getDeclaredMethods()) {
                assertTrue(contract.getParameters()[0].isAnnotationPresent(NotNull.class));
                assertTrue(contract.getParameters()[0].isAnnotationPresent(Valid.class));
                Method implementation = DefaultIngressAccessCommandFacade.class.getMethod(
                        contract.getName(), contract.getParameterTypes());
                assertFalse(implementation.getParameters()[0].isAnnotationPresent(NotNull.class));
                assertFalse(implementation.getParameters()[0].isAnnotationPresent(Valid.class));
                assertEquals(1, factory.getValidator().forExecutables()
                        .validateParameters(facade, implementation, new Object[]{null}).size());
            }
        }
    }

    @Test
    void shouldCascadeCommandValidationWithoutStrengtheningImplementationConstraints() throws Exception {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Method save = DefaultIngressAccessCommandFacade.class.getMethod("saveClientCredentialPolicy",
                    SaveClientCredentialPolicyApiCommand.class);
            SaveClientCredentialPolicyApiCommand invalid = new SaveClientCredentialPolicyApiCommand(
                    "revision", "client", null, null);
            assertEquals(1, factory.getValidator().forExecutables()
                    .validateParameters(facade, save, new Object[]{invalid}).size());
            Method publish = DefaultIngressAccessCommandFacade.class.getMethod("releaseSnapshot",
                    ReleaseSnapshotApiCommand.class);
            assertTrue(factory.getValidator().forExecutables().validateParameters(facade, publish,
                    new Object[]{new ReleaseSnapshotApiCommand()}).isEmpty());
        }
    }
}
