package com.cloud.gateway.interfaces.webflux;

import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.HeaderType;

import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.capability.header.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.capability.header.CookieForwardingMode;
import com.cloud.gateway.domain.capability.header.HeaderConfig;
import com.cloud.gateway.domain.capability.header.TrustedHeaderResolver;
import com.cloud.gateway.domain.capability.header.DeclaredHeaderResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;
import java.util.Locale;

final class RequestHeaderWriter {
    public static final String GATEWAY_PREFIX = "X-GW-";
    ServerWebExchange write(ServerWebExchange exchange,
                            AccessGrantedContext context,
                            HeaderConfig config) {
        String declaredVersion = exchange.getRequest().getHeaders().getFirst(RequestHeader.APP_VERSION.headerName());
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            List.copyOf(headers.keySet()).stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(GATEWAY_PREFIX.toLowerCase(Locale.ROOT)))
                    .forEach(headers::remove);
            RequestHeader.fromType(HeaderType.CONTEXT).forEach(header -> headers.remove(header.headerName()));
            RequestHeader.fromType(HeaderType.SIGNATURE).forEach(header -> headers.remove(header.headerName()));
            config.removeHeaders().forEach(headers::remove);
            config.setHeaders().forEach(headers::set);
            config.trustedHeaders().forEach(source -> {
                String name = source.headerName();
                headers.remove(name);
                TrustedHeaderResolver.resolve(source, context).ifPresent(value -> headers.set(name, value));
            });
            DeclaredHeaderResolver.resolve(RequestHeader.APP_VERSION, declaredVersion)
                    .ifPresent(value -> headers.set(RequestHeader.APP_VERSION.headerName(), value));
            if (config.credentialForwarding().authorizationHeader() == AuthorizationHeaderForwardingMode.STRIP) {
                headers.remove(HttpHeaders.AUTHORIZATION);
            }
            headers.remove(HttpHeaders.COOKIE);
            if (config.credentialForwarding().cookies() == CookieForwardingMode.ALLOW_LIST) {
                config.credentialForwarding().allowedCookieNames().forEach(name ->
                        exchange.getRequest().getCookies().getOrDefault(name, java.util.List.of())
                                .forEach(cookie -> headers.add(HttpHeaders.COOKIE, cookie.toString())));
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

}
