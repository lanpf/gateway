package com.cloud.gateway.infrastructure.security.spring.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank private String adminAudience;
    @Valid private final AccessToken accessToken = new AccessToken();
    @Valid private final RequestSignature requestSignature = new RequestSignature();

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
                    ? StringUtils.hasText(jwt.jwkSetUri)
                    : StringUtils.hasText(reference.introspectionUri)
                    && StringUtils.hasText(reference.clientId)
                    && StringUtils.hasText(reference.clientSecret);
        }
    }

    public enum Format { SELF_CONTAINED, REFERENCE }

    @Getter
    @Setter
    public static class Jwt {
        private String jwkSetUri;
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
}
