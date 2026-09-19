package com.cloud.gateway.domain.policy.header;

import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.common.DomainException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.Set;

/** Only verified context can supply a trusted header; raw request values are never read. */
public final class TrustedHeaderResolver {
    private static final Map<RequestHeader, Function<AccessGrantedContext, String>> READERS = Map.of(
            RequestHeader.APP_ID, context -> context.app().appId().value(),
            RequestHeader.APP_PLATFORM, context -> context.app().platform(),
            RequestHeader.CLIENT_ID, context -> context.client() == null ? null
                    : context.client().clientId().value(),
            RequestHeader.USER_ID, context -> context.subject() == null ? null : context.subject().subjectId().value(),
            RequestHeader.SESSION_ID, context -> context.subject() == null ? null : context.subject().sessionId().value(),
            RequestHeader.SUBJECT_TYPE, context -> context.subject() == null ? null : context.subject().subjectType().name());

    private TrustedHeaderResolver() { }

    public static boolean supports(RequestHeader header) {
        return header != null && READERS.containsKey(header);
    }

    public static Optional<String> resolve(RequestHeader header, AccessGrantedContext context) {
        Require.notNull(context, DomainException::missingField);
        if (!supports(header)) {
            return Optional.empty();
        }
        return Optional.ofNullable(READERS.get(header).apply(context));
    }

    public static Set<RequestHeader> supportedHeaders() {
        return READERS.keySet();
    }
}
