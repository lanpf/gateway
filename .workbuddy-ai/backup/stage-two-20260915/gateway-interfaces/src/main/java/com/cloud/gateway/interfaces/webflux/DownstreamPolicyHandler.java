package com.cloud.gateway.interfaces.webflux;

import com.cloud.gateway.application.governance.CacheRequest;
import com.cloud.gateway.application.governance.DownstreamCircuitBreaker;
import com.cloud.gateway.application.governance.DownstreamExecution;
import com.cloud.gateway.application.governance.DownstreamAdmissionOutput;
import com.cloud.gateway.application.governance.DownstreamGovernanceCoordinator;
import com.cloud.gateway.application.governance.ResponseCache;
import com.cloud.gateway.domain.access.AccessGrantedContext;
import com.cloud.gateway.domain.endpoint.Endpoint;
import com.cloud.gateway.domain.policy.bundle.ResolvedPolicy;
import com.cloud.gateway.domain.policy.capability.DownstreamGovernanceRules;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Adapts authorized request evidence to governance and starts the HTTP response lifecycle. */
public final class DownstreamPolicyHandler {
    private final DownstreamGovernanceCoordinator governance;

    public DownstreamPolicyHandler(ResponseCache cache, DownstreamCircuitBreaker circuits) {
        this.governance = new DownstreamGovernanceCoordinator(cache, circuits);
    }

    Mono<Void> handle(ServerWebExchange original, ServerWebExchange forwarded, GatewayFilterChain chain,
                      Endpoint endpoint, AccessGrantedContext context, ResolvedPolicy policy) {
        return Mono.defer(() -> {
            GatewayResponseWriter writer = new GatewayResponseWriter(forwarded.getResponse());
            CacheRequest request = requestEvidence(original, forwarded, endpoint, context);
            DownstreamAdmissionOutput admission = governance.prepare(request, policy);
            if (admission instanceof DownstreamAdmissionOutput.CacheHit hit) {
                return writer.write(hit.representation());
            }
            DownstreamExecution execution = admission instanceof DownstreamAdmissionOutput.Execute execute
                    ? execute.execution() : ((DownstreamAdmissionOutput.Fallback) admission).execution();
            DownstreamResponseLifecycle lifecycle = new DownstreamResponseLifecycle(forwarded, writer, execution,
                    policy);
            if (admission instanceof DownstreamAdmissionOutput.Fallback fallback) {
                return lifecycle.fallback(fallback.failure(), HttpStatus.SERVICE_UNAVAILABLE);
            }
            return lifecycle.execute(chain);
        });
    }

    private static CacheRequest requestEvidence(ServerWebExchange original, ServerWebExchange forwarded,
                                                Endpoint endpoint, AccessGrantedContext context) {
        return new CacheRequest(endpoint.routeId().value(), endpoint.endpointId().value(),
                context.app().appId().value(), context.client() == null ? null
                        : context.client().clientId().value(),
                original.getRequest().getMethod().name(), original.getRequest().getURI().getRawPath(),
                original.getRequest().getURI().getRawQuery(), original.getRequest().getHeaders(), true,
                new DownstreamGovernanceRules().streaming(endpoint.tags()),
                forwarded.getRequest().getHeaders(), context.subject() != null,
                Boolean.TRUE.equals(original.getAttribute(GatewayRequestAttributes.REQUEST_BODY_PRESENT))
                        || original.getRequest().getHeaders().getContentLength() > 0
                        || original.getRequest().getHeaders().containsKey(HttpHeaders.TRANSFER_ENCODING));
    }
}
