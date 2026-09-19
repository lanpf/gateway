package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.governance.CachedRepresentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

/** Restores gateway-owned metadata both now and after origin beforeCommit callbacks. */
final class GatewayResponseWriter {
    private final ServerHttpResponse response;
    private final Map<String, List<String>> headers;
    private final Map<String, List<ResponseCookie>> cookies;

    GatewayResponseWriter(ServerHttpResponse response) {
        this.response = response;
        this.headers = copy(response.getHeaders());
        this.cookies = copy(response.getCookies());
    }

    Mono<Void> syntheticError(HttpStatus status) {
        return Mono.defer(() -> {
            Runnable prepare = () -> {
                restore();
                response.setStatusCode(status);
                response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
                response.getHeaders().setCacheControl(CacheControl.noStore());
            };
            prepare.run();
            response.beforeCommit(() -> Mono.fromRunnable(prepare));
            return response.setComplete();
        });
    }

    Mono<Void> write(CachedRepresentation representation) {
        return Mono.defer(() -> {
            byte[] body = representation.body();
            Runnable prepare = () -> {
                restore();
                response.setStatusCode(HttpStatusCode.valueOf(representation.status()));
                representation.headers().forEach((name, values) -> response.getHeaders().put(name, values));
                response.getHeaders().setContentLength(body.length);
            };
            prepare.run();
            response.beforeCommit(() -> Mono.fromRunnable(prepare));
            return response.writeWith(Mono.fromSupplier(() -> response.bufferFactory().wrap(body)));
        });
    }

    private static <T> Map<String, List<T>> copy(Map<String, List<T>> source) {
        HashMap<String, List<T>> copy = new HashMap<>();
        source.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Map.copyOf(copy);
    }

    private void restore() {
        response.getHeaders().clear();
        response.getHeaders().putAll(headers);
        response.getCookies().clear();
        response.getCookies().putAll(cookies);
    }
}
