package com.cloud.gateway.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloud.framework.core.AuthenticatedSessionClientRequest;
import com.cloud.framework.core.ClientRequest;
import com.cloud.framework.core.RequestHeader;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.BindingContext;

class GatewayPolicyAdminControllerContractTest {
    private final ClientRequestBodyArgumentResolver resolver = new ClientRequestBodyArgumentResolver(
            ServerCodecConfigurer.create().getReaders(), ReactiveAdapterRegistry.getSharedInstance());

    @Test
    void shouldUseOneAuthenticatedSessionRequestBodyForEveryAdminOperation() {
        Arrays.stream(GatewayPolicyAdminController.class.getDeclaredMethods())
                .filter(method -> Arrays.stream(method.getParameters())
                        .anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class)))
                .forEach(method -> {
                    MethodParameter parameter = new MethodParameter(method, 0);
                    assertTrue(parameter.hasParameterAnnotation(RequestBody.class));
                    assertTrue(parameter.hasParameterAnnotation(jakarta.validation.Valid.class));
                    assertTrue(ClientRequest.class.isAssignableFrom(parameter.getParameterType()));
                    assertTrue(AuthenticatedSessionClientRequest.class.isAssignableFrom(parameter.getParameterType()));
                });
    }

    @Test
    void shouldOverwriteBodyContextWithProtectedHeadersBeforeValidation() throws Exception {
        MethodParameter parameter = parameter("saveClientAppPolicy");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.put("/admin/client-app-policies")
                .contentType(MediaType.APPLICATION_JSON)
                .header(RequestHeader.CLIENT_APP_ID, "trusted-client")
                .header(RequestHeader.CLIENT_PLATFORM, "APP")
                .header(RequestHeader.USER_ID, "trusted-user")
                .header(RequestHeader.SESSION_ID, "trusted-session")
                .body("""
                        {"expectedSourceRevision":"revision-1","status":"ENABLED","platform":"APP","clientAppId":"spoofed-client",
                         "userId":"spoofed-user","sessionId":"spoofed-session"}
                        """));

        GatewayPolicyAdminRequest.SaveClientAppPolicy request = assertInstanceOf(
                GatewayPolicyAdminRequest.SaveClientAppPolicy.class,
                resolver.resolveArgument(parameter, bindingContext(), exchange).block());

        assertEquals("trusted-client", request.getClientAppId());
        assertEquals("spoofed-client", request.getTargetClientAppId());
        assertEquals("trusted-user", request.getUserId());
        assertEquals("trusted-session", request.getSessionId());
    }

    @Test
    void shouldRejectMissingProtectedSessionContext() throws Exception {
        MethodParameter parameter = parameter("saveClientAppPolicy");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.put("/admin/client-app-policies")
                .contentType(MediaType.APPLICATION_JSON)
                .header(RequestHeader.CLIENT_APP_ID, "trusted-client")
                .body("{\"clientAppId\":\"app\",\"status\":\"ENABLED\",\"platform\":\"APP\"}"));

        assertThrows(WebExchangeBindException.class,
                () -> resolver.resolveArgument(parameter, bindingContext(), exchange).block());
    }

    private static MethodParameter parameter(String methodName) {
        Method method = Arrays.stream(GatewayPolicyAdminController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return new MethodParameter(method, 0);
    }

    private static BindingContext bindingContext() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ConfigurableWebBindingInitializer initializer = new ConfigurableWebBindingInitializer();
        initializer.setValidator(validator);
        return new BindingContext(initializer);
    }
}
