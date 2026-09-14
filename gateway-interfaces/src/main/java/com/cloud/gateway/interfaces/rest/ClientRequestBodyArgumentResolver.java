package com.cloud.gateway.interfaces.rest;

import com.cloud.framework.core.RequestHeader;

import com.cloud.framework.core.AuthenticatedSessionContext;
import com.cloud.framework.core.ChannelContext;
import com.cloud.framework.core.ClientRequest;
import com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes;
import com.cloud.gateway.domain.access.IngressAccessGrantedContext;
import com.cloud.gateway.interfaces.header.RequestHeaderValueAdapter;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.support.WebExchangeDataBinder;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.annotation.AbstractMessageReaderArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Binds verified request facts, independently of configurable downstream Header names. */
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
        IngressAccessGrantedContext context = exchange.getAttribute(GatewayRequestAttributes.TRUSTED_CONTEXT);
        request.setClientAppId(context == null ? null : context.app().appId().value());
        request.setClientPlatform(context == null ? null : context.app().platform());
        String version = exchange.getRequest().getHeaders().getFirst(RequestHeader.APP_VERSION.headerName());
        request.setClientVersion(RequestHeaderValueAdapter.sanitizedAppVersion(version).orElse(null));
        if (request instanceof ChannelContext channelContext) {
            channelContext.setChannelCode(context == null || context.client() == null ? null
                    : context.client().clientId().value());
        }
        if (request instanceof AuthenticatedSessionContext sessionContext) {
            sessionContext.setUserId(context == null || context.subject() == null ? null : context.subject().subjectId().value());
            sessionContext.setSessionId(context == null || context.subject() == null ? null : context.subject().sessionId().value());
        }
    }

    private static void validate(
            MethodParameter parameter,
            ClientRequest request,
            BindingContext bindingContext,
            ServerWebExchange exchange
    ) {
        Object[] validationHints = validationHints(parameter);
        if (validationHints == null) {
            return;
        }
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
            if (hints != null) {
                return hints;
            }
        }
        return null;
    }
}
