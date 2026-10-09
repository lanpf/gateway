package com.cloud.gateway.interfaces.rest;

import com.cloud.framework.core.ClientRequest;
import com.cloud.framework.core.Result;
import com.cloud.framework.core.error.FrameworkError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class GatewayValidationBoundaryTest {
    @Test
    void shouldTranslateValidationTriggeredAfterVerifiedFactsWereBound() throws Exception {
        WebTestClient client = WebTestClient.bindToController(new TestController())
                .controllerAdvice(new GatewayValidationExceptionHandler())
                .argumentResolvers(configurer -> configurer.addCustomResolver(new ClientRequestBodyArgumentResolver(
                        ServerCodecConfigurer.create().getReaders(), ReactiveAdapterRegistry.getSharedInstance())))
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(
                            com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes.TRUSTED_CONTEXT,
                            new com.cloud.gateway.domain.ingress.access.AccessGrantedContext(
                                    new com.cloud.gateway.domain.ingress.access.VerifiedApp(
                                            new com.cloud.gateway.domain.app.AppId("test-app"), "WEB"), null, null));
                    return chain.filter(exchange);
                }).build();
        byte[] body = client.post().uri("/validation-boundary").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"\"}").exchange().expectStatus().isBadRequest()
                .expectBody().returnResult().getResponseBody();
        org.junit.jupiter.api.Assertions.assertEquals(FrameworkError.OBJECT_INVALID.errorCode(),
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("code").asText());
        client.post().uri("/validation-boundary").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"valid\"}").exchange().expectStatus().isOk();
    }

    @Getter
    @Setter
    public static class ValidatedRequest extends ClientRequest {
        @NotBlank private String name;
    }

    @RestController
    static class TestController {
        @PostMapping("/validation-boundary")
        public Result<Void> accept(@Valid @ClientRequestBody ValidatedRequest request) { return Result.success(); }
    }
}
