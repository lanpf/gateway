package com.cloud.gateway.interfaces.rest;

import com.cloud.framework.core.ClientRequest;
import com.cloud.framework.core.RequestHeader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.BindingContext;
import static org.junit.jupiter.api.Assertions.*;

class ClientRequestBodyArgumentResolverTest {
    private final ClientRequestBodyArgumentResolver resolver = new ClientRequestBodyArgumentResolver(
            ServerCodecConfigurer.create().getReaders(), ReactiveAdapterRegistry.getSharedInstance());

    @Test
    void shouldBindOnlyValidAppVersionAndOverrideBodyDeclaration() throws Exception {
        for (String version : List.of("1.2.3-beta+4", "a".repeat(64))) {
            assertEquals(version, bind(version).getClientVersion());
        }
        for (String version : List.of("", " 1.2.3", "1.2.3 ", "a".repeat(65), "版本")) {
            assertNull(bind(version).getClientVersion());
        }
        assertNull(bind(null).getClientVersion());
    }

    private ClientRequest bind(String version) throws Exception {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.post("/test")
                .contentType(MediaType.APPLICATION_JSON);
        if (version != null) {
            builder.header(RequestHeader.APP_VERSION.headerName(), version);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.body("{\"clientVersion\":\"forged\"}"));
        MethodParameter parameter = new MethodParameter(Endpoint.class.getDeclaredMethod("accept", ClientRequest.class), 0);
        return assertInstanceOf(ClientRequest.class,
                resolver.resolveArgument(parameter, new BindingContext(), exchange).block());
    }

    private static final class Endpoint {
        void accept(@RequestBody ClientRequest request) { }
    }
}
