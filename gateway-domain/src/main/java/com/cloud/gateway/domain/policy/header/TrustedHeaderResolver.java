package com.cloud.gateway.domain.policy.header;

import com.cloud.framework.core.RequestHeader;
import com.cloud.framework.core.validation.Require;
import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.domain.common.DomainException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.Set;

/** Only verified context can supply a trusted header; raw request values are never read. */
public final class TrustedHeaderResolver {
    private static final Map<RequestHeader, Function<IngressAccessGrantedContext, String>> READERS = Map.of(
            RequestHeader.APP_ID, context -> context.appContext().appId().value(),
            RequestHeader.APP_PLATFORM, context -> context.appContext().platform(),
            RequestHeader.CLIENT_ID, context -> context.clientContext() == null ? null
                    : context.clientContext().clientId().value(),
            RequestHeader.USER_ID, context -> context.subjectContext() == null ? null : context.subjectContext().subjectId(),
            RequestHeader.SESSION_ID, context -> context.subjectContext() == null ? null : context.subjectContext().sessionId(),
            RequestHeader.SUBJECT_TYPE, context -> context.subjectContext() == null ? null : context.subjectContext().subjectType().name());

    private TrustedHeaderResolver() { }

    public static boolean supports(RequestHeader header) {
        return header != null && READERS.containsKey(header);
    }

    public static Optional<String> resolve(RequestHeader header, IngressAccessGrantedContext context) {
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
