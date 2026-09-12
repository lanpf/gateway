package com.cloud.gateway.infrastructure.persistence.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyHistoryProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.history.NacosPolicyHistorySourceAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

class NacosPolicyHistorySourceAdapterTest {
    private static final String BASE_URL = "http://127.0.0.1:8848/nacos";

    @Test
    void shouldReadHistoryDetailsUntilBusinessVersionMatches() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NacosPolicyHistoryProperties historyProperties = new NacosPolicyHistoryProperties();
        historyProperties.setAdminAccessToken("test-token");
        NacosPolicyHistorySourceAdapter client = client(builder.build(), historyProperties, new NacosConfigProperties());
        expectRequest(server, BASE_URL
                + "/v3/admin/cs/history/list?dataId=gateway-policy.yaml&groupName=GATEWAY"
                + "&namespaceId=public&pageNo=1&pageSize=100", "test-token")
                .andRespond(withSuccess("""
                        {"code":0,"message":"success","data":{"pageItems":[{"id":"2"},{"id":"1"}]}}
                        """, MediaType.APPLICATION_JSON));
        expectHistoryDetail(server, "2", "version: 2", "test-token");
        expectHistoryDetail(server, "1", "version: 1", "test-token");

        Optional<String> result = client.findFirst(content -> content.contains("version: 1"));

        assertEquals(Optional.of("version: 1"), result);
        server.verify();
    }

    @Test
    void shouldLoginOnceAndUseReturnedAccessToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NacosConfigProperties nacosProperties = new NacosConfigProperties();
        nacosProperties.setUsername("nacos-user");
        nacosProperties.setPassword("nacos-password");
        NacosPolicyHistorySourceAdapter client = client(
                builder.build(), new NacosPolicyHistoryProperties(), nacosProperties);
        expectRequest(server, BASE_URL + "/v3/auth/user/login", null)
                .andRespond(withSuccess("""
                        {"accessToken":"login-token","tokenTtl":300}
                        """, MediaType.APPLICATION_JSON));
        expectRequest(server, BASE_URL
                + "/v3/admin/cs/history/list?dataId=gateway-policy.yaml&groupName=GATEWAY"
                + "&namespaceId=public&pageNo=1&pageSize=100", "login-token")
                .andRespond(withSuccess("""
                        {"code":0,"message":"success","data":{"pageItems":[]}}
                        """, MediaType.APPLICATION_JSON));

        assertEquals(Optional.empty(), client.findFirst(content -> true));
        server.verify();
    }

    private static NacosPolicyHistorySourceAdapter client(
            RestClient restClient,
            NacosPolicyHistoryProperties historyProperties,
            NacosConfigProperties nacosProperties
    ) {
        return new NacosPolicyHistorySourceAdapter(
                restClient,
                new NacosPolicyProperties(),
                historyProperties,
                nacosProperties,
                Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private static void expectHistoryDetail(
            MockRestServiceServer server,
            String historyId,
            String content,
            String accessToken
    ) {
        expectRequest(server, BASE_URL
                + "/v3/admin/cs/history?dataId=gateway-policy.yaml&groupName=GATEWAY"
                + "&namespaceId=public&nid=" + historyId, accessToken)
                .andRespond(withSuccess("""
                        {"code":0,"message":"success","data":{"id":"%s","content":"%s"}}
                        """.formatted(historyId, content), MediaType.APPLICATION_JSON));
    }

    private static ResponseActions expectRequest(
            MockRestServiceServer server,
            String uri,
            String accessToken
    ) {
        return server.expect(request -> {
            assertEquals(uri, request.getURI().toString());
            if (accessToken != null) {
                assertEquals(accessToken, request.getHeaders().getFirst("accessToken"));
            }
        });
    }
}
