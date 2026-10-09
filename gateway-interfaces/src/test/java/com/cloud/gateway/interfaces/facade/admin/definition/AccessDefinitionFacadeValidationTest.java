package com.cloud.gateway.interfaces.facade.admin.definition;

import com.cloud.gateway.api.facade.admin.definition.AccessDefinitionCommandFacade;
import com.cloud.gateway.api.facade.admin.definition.SetAppStatusApiCommand;
import com.cloud.gateway.application.configuration.editing.*;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessDefinitionFacadeValidationTest {
    @Test
    void shouldValidateInterfaceContractThroughTheSpringProxyBeforeMapping() {
        AccessDefinitionApiMapper mapper = mock(AccessDefinitionApiMapper.class);
        AppAccessCommandService service = mock(AppAccessCommandService.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MethodValidationPostProcessor.class);
            context.registerBean(DefaultAccessDefinitionCommandFacade.class,
                    () -> new DefaultAccessDefinitionCommandFacade(service,
                            mock(ClientAccessCommandService.class), mock(ClientCredentialCommandService.class),
                            mock(EndpointAccessCommandService.class), mapper,
                            mock(CapabilityPolicyConfigCommandService.class), mock(CapabilityPolicyConfigApiMapper.class)));
            context.refresh();
            AccessDefinitionCommandFacade facade = context.getBean(AccessDefinitionCommandFacade.class);
            assertThrows(ConstraintViolationException.class, () -> facade.saveApp(null));
            assertThrows(ConstraintViolationException.class,
                    () -> facade.setAppStatus(new SetAppStatusApiCommand(null, null, null)));
            verifyNoInteractions(mapper, service);
        }
    }
}
