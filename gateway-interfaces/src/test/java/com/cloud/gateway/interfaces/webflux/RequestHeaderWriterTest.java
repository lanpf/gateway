package com.cloud.gateway.interfaces.webflux;

import com.cloud.framework.core.HeaderType;
import com.cloud.framework.core.RequestHeader;

import static org.junit.jupiter.api.Assertions.*;

import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.client.CredentialId;
import com.cloud.gateway.domain.client.ClientId;
import com.cloud.gateway.domain.policy.AuthorizationHeaderForwardingMode;
import com.cloud.gateway.domain.policy.CookieForwardingMode;
import com.cloud.gateway.domain.policy.CredentialForwarding;
import com.cloud.gateway.domain.policy.header.HeaderPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

class RequestHeaderWriterTest {
    private final RequestHeaderWriter writer = new RequestHeaderWriter();

    @Test
    void shouldRejectControlCharactersFromEveryDynamicTrustedSource() {
        for (RequestHeader source : List.of(RequestHeader.APP_ID, RequestHeader.APP_PLATFORM,
                RequestHeader.CLIENT_ID, RequestHeader.USER_ID, RequestHeader.SESSION_ID)) {
            HeaderPolicy policy = new HeaderPolicy(Set.of(source), Map.of(), Set.of(), null);
            for (char control : new char[]{'\r', '\n', '\t', '\0', '\u001f', '\u007f', '\u0085', '\u009f'}) {
                IngressAccessGrantedContext context = contextWithDynamicValue("before" + control + "after");
                MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
                assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                        () -> writer.write(exchange, context, policy, Set.of()),
                        source + " control=" + (int) control);
            }
        }
    }

    @Test
    void shouldAccept4096CharactersAndReject4097ForEveryDynamicTrustedSource() {
        for (RequestHeader source : List.of(RequestHeader.APP_ID, RequestHeader.APP_PLATFORM,
                RequestHeader.CLIENT_ID, RequestHeader.USER_ID, RequestHeader.SESSION_ID)) {
            HeaderPolicy policy = new HeaderPolicy(Set.of(source), Map.of(), Set.of(), null);
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));
            ServerWebExchange output = writer.write(exchange, contextWithDynamicValue("a".repeat(4096)), policy, Set.of());
            assertEquals("a".repeat(4096), output.getRequest().getHeaders().getFirst(source.headerName()), source.name());
            IngressAccessGrantedContext oversized = contextWithDynamicValue("a".repeat(4097));
            assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                    () -> writer.write(exchange, oversized, policy, Set.of()), source.name());
        }
    }

    private static IngressAccessGrantedContext contextWithDynamicValue(String value) {
        return new IngressAccessGrantedContext(new IngressAccessGrantedContext.AppContext(new AppId(value), value),
                new IngressAccessGrantedContext.ClientContext(new ClientId(value), new CredentialId("client")),
                new IngressAccessGrantedContext.SubjectContext(SubjectType.HOST_SESSION, value, value, Set.of(), Set.of()));
    }

    @Test
    void shouldRejectConstantInjectionIntoIdentityAndUnsupportedTrustedHeaders() {
        assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                () -> new HeaderPolicy(Set.of(), Map.of(RequestHeader.APP_ID.headerName(), "forged"), Set.of(), null));
        assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                () -> new HeaderPolicy(Set.of(RequestHeader.APP_VERSION), Map.of(), Set.of(), null));
        assertThrows(com.cloud.gateway.domain.common.DomainException.class,
                () -> new HeaderPolicy(Set.of(RequestHeader.SIGNATURE), Map.of(), Set.of(), null));
    }

    @Test
    void shouldReplaceForgedValuesWithTrustedFacts() {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/test");
        RequestHeader.fromType(HeaderType.CONTEXT).stream().map(RequestHeader::headerName).collect(java.util.stream.Collectors.toUnmodifiableSet()).forEach(name -> request.header(name.toUpperCase(java.util.Locale.ROOT), "forged", "duplicate"));
        HeaderPolicy policy = HeaderPolicy.defaults(CredentialForwarding.stripAll());
        HttpHeaders output = writer.write(MockServerWebExchange.from(request), context(true, true), policy,
                RequestHeader.fromType(com.cloud.framework.core.HeaderType.CONTEXT).stream().map(RequestHeader::headerName).collect(java.util.stream.Collectors.toUnmodifiableSet())).getRequest().getHeaders();

        assertEquals(List.of("app"), output.get(RequestHeader.APP_ID.headerName()));
        assertEquals("WEB", output.getFirst(RequestHeader.APP_PLATFORM.headerName()));
        assertEquals("verified-client", output.getFirst(RequestHeader.CLIENT_ID.headerName()));
        assertEquals("user", output.getFirst(RequestHeader.USER_ID.headerName()));
        assertEquals("session", output.getFirst(RequestHeader.SESSION_ID.headerName()));
        assertEquals("HOST_SESSION", output.getFirst(RequestHeader.SUBJECT_TYPE.headerName()));
    }

    @Test
    void shouldNotPromoteDeclaredClientOrKeepForgedSubjectWhenAuthenticationIsAbsent() {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/test")
                .header(RequestHeader.CLIENT_ID.headerName(), "declared-client")
                .header(RequestHeader.USER_ID.headerName(), "forged-user")
                .header(RequestHeader.SESSION_ID.headerName(), "forged-session")
                .header(RequestHeader.SUBJECT_TYPE.headerName(), "HOST_SESSION");
        HttpHeaders output = writer.write(MockServerWebExchange.from(request), context(false, false),
                HeaderPolicy.defaults(CredentialForwarding.stripAll()), RequestHeader.fromType(HeaderType.CONTEXT).stream().map(RequestHeader::headerName).collect(java.util.stream.Collectors.toUnmodifiableSet()))
                .getRequest().getHeaders();
        for (String name : List.of(RequestHeader.CLIENT_ID.headerName(), RequestHeader.USER_ID.headerName(), RequestHeader.SESSION_ID.headerName(), RequestHeader.SUBJECT_TYPE.headerName())) {
            assertFalse(output.containsKey(name), name);
        }
        assertEquals("app", output.getFirst(RequestHeader.APP_ID.headerName()));
    }

    @Test
    void shouldWriteFixedTrustedNamesAndClearUnconfiguredGatewayHeaders() {
        HeaderPolicy policy = new HeaderPolicy(Set.of(RequestHeader.APP_ID, RequestHeader.CLIENT_ID, RequestHeader.USER_ID),
                Map.of("X-Deployment-Tag", "managed"), Set.of("X-Remove-Me"), CredentialForwarding.stripAll());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
                .header("X-Gateway-Partner", "forged")
                .header("X-Gateway-User", "forged")
                .header("X-Gateway-Retired-User", "forged")
                .header("x-GaTeWaY-No-Longer-Mapped", "forged-retired")
                .header("X-Remove-Me", "external")
                .header("X-Deployment-Tag", "external")
                .header("Accept-Language", "zh-CN"));
        HttpHeaders output = writer.write(exchange, context(true, true), policy,
                Set.of("X-Gateway-Partner", "X-Gateway-User", "X-Gateway-Retired-User")).getRequest().getHeaders();
        assertEquals("verified-client", output.getFirst(RequestHeader.CLIENT_ID.headerName()));
        assertEquals("app", output.getFirst(RequestHeader.APP_ID.headerName()));
        assertEquals("user", output.getFirst(RequestHeader.USER_ID.headerName()));
        assertFalse(output.containsKey("X-Gateway-Partner"));
        assertFalse(output.containsKey("X-Gateway-User"));
        assertEquals("managed", output.getFirst("X-Deployment-Tag"));
        assertFalse(output.containsKey("X-Gateway-Retired-User"));
        assertFalse(output.containsKey("X-Gateway-No-Longer-Mapped"));
        assertFalse(output.containsKey("X-Remove-Me"));
        assertEquals("zh-CN", output.getFirst("Accept-Language"));
        HttpHeaders anonymous = writer.write(exchange, context(false, false), policy, Set.of("X-Gateway-Retired-User"))
                .getRequest().getHeaders();
        assertFalse(anonymous.containsKey("X-Gateway-Partner"));
        assertFalse(anonymous.containsKey("X-Gateway-User"));
    }

    @Test
    void shouldStripAuthorizationCookiesAndAllSignatureEvidenceByDefault() {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/test").header(HttpHeaders.AUTHORIZATION, "Bearer secret")
                .cookie(new HttpCookie("session", "secret"));
        for (String name : List.of(RequestHeader.SIGNATURE.headerName(), RequestHeader.TIMESTAMP.headerName(),
                RequestHeader.NONCE.headerName(), RequestHeader.CREDENTIAL_ID.headerName())) {
            request.header(name, "secret");
        }
        HttpHeaders output = writer.write(MockServerWebExchange.from(request), context(true, true),
                HeaderPolicy.defaults(CredentialForwarding.stripAll()), RequestHeader.fromType(HeaderType.CONTEXT).stream().map(RequestHeader::headerName).collect(java.util.stream.Collectors.toUnmodifiableSet()))
                .getRequest().getHeaders();
        for (String name : List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, RequestHeader.SIGNATURE.headerName(),
                RequestHeader.TIMESTAMP.headerName(), RequestHeader.NONCE.headerName(), RequestHeader.CREDENTIAL_ID.headerName())) {
            assertFalse(output.containsKey(name), name);
        }
    }

    @Test
    void shouldPreserveAuthorizationAndOnlyExplicitlyAllowedCookies() {
        CredentialForwarding forwarding = new CredentialForwarding(AuthorizationHeaderForwardingMode.PRESERVE,
                CookieForwardingMode.ALLOW_LIST, Set.of("locale"));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer downstream-token")
                .cookie(new HttpCookie("locale", "zh-CN"), new HttpCookie("session", "secret"),
                        new HttpCookie("Locale", "case-sensitive")));
        HttpHeaders output = writer.write(exchange, context(false, false), HeaderPolicy.defaults(forwarding),
                RequestHeader.fromType(HeaderType.CONTEXT).stream().map(RequestHeader::headerName).collect(java.util.stream.Collectors.toUnmodifiableSet())).getRequest().getHeaders();
        assertEquals("Bearer downstream-token", output.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(List.of("locale=zh-CN"), output.get(HttpHeaders.COOKIE));
    }

    @Test
    void shouldKeepOnlyBoundedSanitizedAppVersion() {
        for (String version : List.of("1.2.3-beta+4", "x".repeat(64))) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").header(RequestHeader.APP_VERSION.headerName(), version));
            HttpHeaders output = writer.write(exchange, context(false, false), HeaderPolicy.defaults(null), RequestHeader.fromType(HeaderType.CONTEXT).stream().map(RequestHeader::headerName).collect(java.util.stream.Collectors.toUnmodifiableSet()))
                    .getRequest().getHeaders();
            assertEquals(version, output.getFirst(RequestHeader.APP_VERSION.headerName()));
        }
        for (String version : List.of("", "invalid version", " 1.2.3", "1.2.3 ", "版本", "x".repeat(65))) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test")
                    .header(RequestHeader.APP_VERSION.headerName(), version));
            HttpHeaders output = writer.write(exchange, context(false, false), HeaderPolicy.defaults(null), Set.of())
                    .getRequest().getHeaders();
            assertNull(output.getFirst(RequestHeader.APP_VERSION.headerName()));
        }
    }

    private static IngressAccessGrantedContext context(boolean verified, boolean subject) {
        return new IngressAccessGrantedContext(new IngressAccessGrantedContext.AppContext(new AppId("app"), "WEB"),
                verified ? new IngressAccessGrantedContext.ClientContext(new ClientId("verified-client"),
                        new CredentialId("client")) : null,
                subject ? new IngressAccessGrantedContext.SubjectContext(SubjectType.HOST_SESSION, "user", "session",
                        Set.of("service"), Set.of("read")) : null);
    }
}
