package com.cloud.gateway.interfaces.webflux;

import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.HeaderType;

import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import com.cloud.gateway.domain.policy.header.HeaderValueRules;
import com.cloud.gateway.domain.policy.header.TrustedHeaderResolver;
import com.cloud.gateway.interfaces.header.RequestHeaderValueAdapter;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

final class RequestHeaderWriter {
    ServerWebExchange write(ServerWebExchange exchange, IngressAccessGrantedContext context,
            HeaderPolicy policy, Set<String> allTrustedNames) {
        String declaredVersion = exchange.getRequest().getHeaders().getFirst(RequestHeader.APP_VERSION.headerName());
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            java.util.List.copyOf(headers.keySet()).stream()
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(HeaderPolicy.GATEWAY_PREFIX))
                    .forEach(headers::remove);
            allTrustedNames.forEach(headers::remove);
            RequestHeader.fromType(HeaderType.CONTEXT).forEach(header -> headers.remove(header.headerName()));
            RequestHeader.fromType(HeaderType.SIGNATURE).forEach(header -> headers.remove(header.headerName()));
            policy.removeHeaders().forEach(headers::remove);
            policy.setHeaders().forEach(headers::set);
            policy.trustedHeaders().forEach(source -> {
                String name = source.headerName();
                headers.remove(name);
                String value = TrustedHeaderResolver.resolve(source, context).orElse(null);
                if (value != null) {
                    HeaderValueRules.requireValid(value);
                    headers.set(name, value);
                }
            });
            RequestHeaderValueAdapter.sanitizedAppVersion(declaredVersion)
                    .ifPresent(value -> headers.set(RequestHeader.APP_VERSION.headerName(), value));
            if (policy.credentialForwarding().authorizationHeader() == AuthorizationHeaderForwardingMode.STRIP) {
                headers.remove(HttpHeaders.AUTHORIZATION);
            }
            headers.remove(HttpHeaders.COOKIE);
            if (policy.credentialForwarding().cookies() == CookieForwardingMode.ALLOW_LIST) {
                policy.credentialForwarding().allowedCookieNames().forEach(name ->
                        exchange.getRequest().getCookies().getOrDefault(name, java.util.List.of())
                                .forEach(cookie -> headers.add(HttpHeaders.COOKIE, cookie.toString())));
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

}
