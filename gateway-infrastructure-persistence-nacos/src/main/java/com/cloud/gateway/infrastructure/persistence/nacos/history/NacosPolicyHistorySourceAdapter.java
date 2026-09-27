package com.cloud.gateway.infrastructure.persistence.nacos.history;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.PersistenceNacosError;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyHistoryProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.config.NacosPolicyProperties;
import com.cloud.gateway.infrastructure.persistence.nacos.history.payload.NacosApiPayload;
import com.cloud.gateway.infrastructure.persistence.nacos.history.payload.NacosHistoryDetailPayload;
import com.cloud.gateway.infrastructure.persistence.nacos.history.payload.NacosHistoryItemPayload;
import com.cloud.gateway.infrastructure.persistence.nacos.history.payload.NacosHistoryPagePayload;
import com.cloud.gateway.infrastructure.persistence.nacos.history.payload.NacosLoginPayload;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RequiredArgsConstructor
public final class NacosPolicyHistorySourceAdapter implements NacosPolicyHistorySource {
    private static final String ACCESS_TOKEN_HEADER = "accessToken";
    private static final String LOGIN_PATH = "/v3/auth/user/login";
    private static final String HISTORY_LIST_PATH = "/v3/admin/cs/history/list";
    private static final String HISTORY_DETAIL_PATH = "/v3/admin/cs/history";
    private static final long TOKEN_EXPIRY_SAFETY_SECONDS = 30;
    private static final ParameterizedTypeReference<NacosApiPayload<NacosHistoryPagePayload>> HISTORY_PAGE_TYPE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<NacosApiPayload<NacosHistoryDetailPayload>> HISTORY_DETAIL_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final NacosPolicyProperties policyProperties;
    private final NacosPolicyHistoryProperties historyProperties;
    private final NacosConfigProperties nacosProperties;
    private final Clock clock;
    private volatile AccessToken accessToken;

    @Override
    public Optional<String> findFirst(Predicate<String> contentPredicate) {
        try {
            return findFirstAvailable(contentPredicate);
        } catch (RestClientException exception) {
            throw new InfrastructureException(PersistenceNacosError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private Optional<String> findFirstAvailable(Predicate<String> contentPredicate) {
        for (int pageNumber = 1; pageNumber <= historyProperties.getMaxPages(); pageNumber++) {
            List<NacosHistoryItemPayload> items = historyPage(pageNumber);
            if (CollectionUtils.isEmpty(items)) {
                return Optional.empty();
            }
            for (NacosHistoryItemPayload item : items) {
                String content = historyDetail(requiredText(item.id()));
                if (contentPredicate.test(content)) {
                    return Optional.of(content);
                }
            }
            if (items.size() < historyProperties.getPageSize()) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private List<NacosHistoryItemPayload> historyPage(int pageNumber) {
        NacosApiPayload<NacosHistoryPagePayload> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(HISTORY_LIST_PATH)
                        .queryParam("dataId", policyProperties.getDataId())
                        .queryParam("groupName", policyProperties.getGroup())
                        .queryParam("namespaceId", namespace())
                        .queryParam("pageNo", pageNumber)
                        .queryParam("pageSize", historyProperties.getPageSize())
                        .build())
                .headers(this::addAccessToken)
                .retrieve()
                .body(HISTORY_PAGE_TYPE);
        NacosHistoryPagePayload page = requiredData(response);
        return page.pageItems() == null ? List.of() : List.copyOf(page.pageItems());
    }

    private String historyDetail(String historyId) {
        NacosApiPayload<NacosHistoryDetailPayload> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(HISTORY_DETAIL_PATH)
                        .queryParam("dataId", policyProperties.getDataId())
                        .queryParam("groupName", policyProperties.getGroup())
                        .queryParam("namespaceId", namespace())
                        .queryParam("nid", historyId)
                        .build())
                .headers(this::addAccessToken)
                .retrieve()
                .body(HISTORY_DETAIL_TYPE);
        return requiredText(requiredData(response).content());
    }

    private void addAccessToken(HttpHeaders headers) {
        String token = token();
        if (StringUtils.hasText(token)) {
            headers.set(ACCESS_TOKEN_HEADER, token);
        }
    }

    private String token() {
        if (StringUtils.hasText(historyProperties.getAdminAccessToken())) {
            return historyProperties.getAdminAccessToken().trim();
        }
        if (!StringUtils.hasText(nacosProperties.getUsername())
                || !StringUtils.hasText(nacosProperties.getPassword())) {
            return null;
        }
        AccessToken observed = accessToken;
        if (observed != null && observed.expiresAt().isAfter(clock.instant())) {
            return observed.value();
        }
        synchronized (this) {
            observed = accessToken;
            if (observed == null || !observed.expiresAt().isAfter(clock.instant())) {
                accessToken = login();
            }
            return accessToken.value();
        }
    }

    private AccessToken login() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", nacosProperties.getUsername());
        form.add("password", nacosProperties.getPassword());
        NacosLoginPayload response = restClient.post()
                .uri(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(NacosLoginPayload.class);
        if (response == null || !StringUtils.hasText(response.accessToken()) || response.tokenTtl() == null
                || response.tokenTtl() <= TOKEN_EXPIRY_SAFETY_SECONDS) {
            throw invalidContent();
        }
        return new AccessToken(response.accessToken(),
                clock.instant().plusSeconds(response.tokenTtl() - TOKEN_EXPIRY_SAFETY_SECONDS));
    }

    private String namespace() {
        return StringUtils.hasText(nacosProperties.getNamespace())
                ? nacosProperties.getNamespace().trim()
                : NacosConfigProperties.DEFAULT_NAMESPACE;
    }

    private static <T> T requiredData(NacosApiPayload<T> response) {
        if (response == null || response.code() == null) {
            throw invalidContent();
        }
        if (response.code() != 0) {
            throw new InfrastructureException(PersistenceNacosError.INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE);
        }
        if (response.data() == null) {
            throw invalidContent();
        }
        return response.data();
    }

    private static String requiredText(String value) {
        if (!StringUtils.hasText(value)) {
            throw invalidContent();
        }
        return value;
    }

    private static InfrastructureException invalidContent() {
        return new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
    }

    private record AccessToken(String value, Instant expiresAt) { }
}
