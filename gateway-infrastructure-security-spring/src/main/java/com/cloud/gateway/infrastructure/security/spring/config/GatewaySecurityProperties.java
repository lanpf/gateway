package com.cloud.gateway.infrastructure.security.spring.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(GatewaySecurityProperties.PREFIX)
@Getter
@Setter
public class GatewaySecurityProperties {
    public static final String PREFIX = "gateway.security";
    public static final String ACCESS_TOKEN_PREFIX = PREFIX + ".access-token";

    @Valid private final AccessToken accessToken = new AccessToken();
    @Valid private final RequestSignature requestSignature = new RequestSignature();
    @Valid private final BrowserSession browserSession = new BrowserSession();

    @Getter
    @Setter
    public static class AccessToken {
        @NotNull private Format format;
        @NotBlank private String issuer;
        @Valid private final Jwt jwt = new Jwt();
        @Valid private final Reference reference = new Reference();
        @AssertTrue(message = "The selected Access Token format is missing required configuration")
        public boolean isSelectedFormatConfigured() {
            return format == Format.SELF_CONTAINED
                    ? StringUtils.hasText(jwt.setUri)
                    : StringUtils.hasText(reference.introspectionUri)
                    && StringUtils.hasText(reference.clientId)
                    && StringUtils.hasText(reference.clientSecret);
        }
    }

    public enum Format { SELF_CONTAINED, REFERENCE }

    @Getter
    @Setter
    public static class Jwt {
        private String setUri;
    }

    @Getter
    @Setter
    public static class Reference {
        private String introspectionUri;
        private String clientId;
        private String clientSecret;
    }

    @Getter
    @Setter
    public static class RequestSignature {
        @NotNull @DurationMin(seconds = 1)
        private Duration maxClockSkew = Duration.ofMinutes(5);
        private final Map<String, String> secrets = new LinkedHashMap<>();
    }

    /** BROWSER_SESSION Cookie 的在线验证服务配置。 */
    @Getter
    @Setter
    public static class BrowserSession {
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*")
        private String serviceId = "user-auth";
    }
}
