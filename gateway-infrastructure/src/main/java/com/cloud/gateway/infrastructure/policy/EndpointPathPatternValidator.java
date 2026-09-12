package com.cloud.gateway.infrastructure.policy;

import com.cloud.gateway.domain.endpoint.EndpointRegistry;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.endpoint.EndpointMatcher;
import com.cloud.gateway.infrastructure.common.InfrastructureError;
import com.cloud.gateway.infrastructure.common.InfrastructureException;
import org.springframework.web.util.pattern.PathPatternParser;

/** Validate the transport's path grammar before publishing or activating any configuration. */
final class EndpointPathPatternValidator {
    private EndpointPathPatternValidator() { }

    static void validate(EndpointRegistry registry) {
        PathPatternParser parser = new PathPatternParser();
        try {
            for (Endpoint endpoint : registry.definitions()) {
                for (EndpointMatcher matcher : endpoint.matchers()) {
                    if (!matcher.pathPattern().startsWith("/")) {
                        throw new IllegalArgumentException("Absolute endpoint path required");
                    }
                    parser.parse(matcher.pathPattern());
                }
            }
        } catch (IllegalArgumentException invalidPattern) {
            // Do not include the full policy document in the adapter error.
            throw new InfrastructureException(InfrastructureError.INFRA_ACL_NACOS_CONTENT_INVALID);
        }
    }
}
