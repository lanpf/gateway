package com.cloud.gateway.interfaces.rest;

import com.cloud.framework.core.AuthenticatedSessionContext;
import com.cloud.framework.core.ChannelContext;
import com.cloud.framework.core.ClientRequest;
import com.cloud.framework.core.RequestHeader;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.support.WebExchangeDataBinder;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.annotation.AbstractMessageReaderArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Binds gateway-rewritten protected headers before validating a REST request body. */
public final class ClientRequestBodyArgumentResolver extends AbstractMessageReaderArgumentResolver {
    public ClientRequestBodyArgumentResolver(List<HttpMessageReader<?>> readers, ReactiveAdapterRegistry registry) {
        super(readers, registry);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestBody.class)
                && ClientRequest.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Mono<Object> resolveArgument(
            MethodParameter parameter, BindingContext bindingContext, ServerWebExchange exchange) {
        RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
        return readBody(withoutValidation(parameter), requestBody.required(), bindingContext, exchange)
                .cast(ClientRequest.class)
                .doOnNext(request -> bindProtectedHeaders(request, exchange))
                .doOnNext(request -> validate(parameter, request, bindingContext, exchange))
                .cast(Object.class);
    }

    private static MethodParameter withoutValidation(MethodParameter parameter) {
        return new MethodParameter(parameter) {
            @Override
            public Annotation[] getParameterAnnotations() {
                return Arrays.stream(super.getParameterAnnotations())
                        .filter(annotation -> ValidationAnnotationUtils.determineValidationHints(annotation) == null)
                        .toArray(Annotation[]::new);
            }
        };
    }

    private static void bindProtectedHeaders(ClientRequest request, ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        request.setClientAppId(headers.getFirst(RequestHeader.CLIENT_APP_ID));
        request.setClientPlatform(headers.getFirst(RequestHeader.CLIENT_PLATFORM));
        request.setClientVersion(headers.getFirst(RequestHeader.CLIENT_VERSION));
        if (request instanceof ChannelContext channelContext) {
            channelContext.setChannelCode(headers.getFirst(RequestHeader.CHANNEL_CODE));
        }
        if (request instanceof AuthenticatedSessionContext sessionContext) {
            sessionContext.setUserId(headers.getFirst(RequestHeader.USER_ID));
            sessionContext.setSessionId(headers.getFirst(RequestHeader.SESSION_ID));
        }
    }

    private static void validate(
            MethodParameter parameter,
            ClientRequest request,
            BindingContext bindingContext,
            ServerWebExchange exchange
    ) {
        Object[] validationHints = validationHints(parameter);
        if (validationHints == null) return;
        String name = Conventions.getVariableNameForParameter(parameter);
        WebExchangeDataBinder binder = bindingContext.createDataBinder(
                exchange, request, name, ResolvableType.forMethodParameter(parameter));
        binder.validate(validationHints);
        if (binder.getBindingResult().hasErrors()) {
            throw new WebExchangeBindException(parameter, binder.getBindingResult());
        }
    }

    private static Object[] validationHints(MethodParameter parameter) {
        for (Annotation annotation : parameter.getParameterAnnotations()) {
            Object[] hints = ValidationAnnotationUtils.determineValidationHints(annotation);
            if (hints != null) return hints;
        }
        return null;
    }
}
