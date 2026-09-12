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

import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.app.AppId;
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
    void shouldBindVerifiedContextIndependentlyOfConfigurableHeaders() throws Exception {
        MethodParameter parameter = parameter("saveAppPolicy");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.put("/admin/app-policies")
                .contentType(MediaType.APPLICATION_JSON)
                .header(RequestHeader.APP_ID.headerName(), "trusted-client")
                .header(RequestHeader.APP_PLATFORM.headerName(), "APP")
                .header(RequestHeader.USER_ID.headerName(), "forged-user")
                .header(RequestHeader.SESSION_ID.headerName(), "forged-session")
                .body("""
                        {"expectedSourceRevision":"revision-1","status":"ENABLED","platform":"APP","appId":"spoofed-client",
                         "userId":"spoofed-user","sessionId":"spoofed-session"}
                        """));

        exchange.getAttributes().put(com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes.TRUSTED_CONTEXT,
                new IngressAccessGrantedContext(
                        new IngressAccessGrantedContext.AppContext(
                                new AppId("trusted-client"), "APP"), null,
                        new IngressAccessGrantedContext.SubjectContext(
                                SubjectType.HOST_SESSION, "trusted-user", "trusted-session",
                                java.util.Set.of("gateway"), java.util.Set.of("admin"))));

        GatewayPolicyAdminRequest.SaveAppPolicy request = assertInstanceOf(
                GatewayPolicyAdminRequest.SaveAppPolicy.class,
                resolver.resolveArgument(parameter, bindingContext(), exchange).block());

        assertEquals("trusted-client", request.getClientAppId());
        assertEquals("spoofed-client", request.getTargetAppId());
        assertEquals("trusted-user", request.getUserId());
        assertEquals("trusted-session", request.getSessionId());
    }

    @Test
    void shouldRejectMissingProtectedSessionContext() throws Exception {
        MethodParameter parameter = parameter("saveAppPolicy");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.put("/admin/app-policies")
                .contentType(MediaType.APPLICATION_JSON)
                .header(RequestHeader.APP_ID.headerName(), "trusted-client")
                .body("{\"appId\":\"app\",\"status\":\"ENABLED\",\"platform\":\"APP\"}"));

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
