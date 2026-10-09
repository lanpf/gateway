package com.cloud.gateway.interfaces.rest;

import static com.cloud.gateway.api.constants.GatewayPathApiConstants.ADMIN_POLICY_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.*;

import com.cloud.framework.core.SubjectType;
import com.cloud.framework.core.error.FrameworkException;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.framework.lock.LockContext;
import com.cloud.framework.lock.LockExecutor;
import com.cloud.gateway.api.facade.admin.definition.*;
import com.cloud.gateway.application.common.ApplicationError;
import com.cloud.gateway.application.common.ApplicationException;
import com.cloud.gateway.application.configuration.definition.*;
import com.cloud.gateway.application.configuration.editing.CapabilityPolicyConfigCommandService;
import com.cloud.gateway.application.configuration.publication.*;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.domain.capability.catalog.BuiltInCapabilityCatalog;
import com.cloud.gateway.domain.capability.policy.*;
import com.cloud.gateway.domain.capability.header.HeaderConfigRule;
import com.cloud.gateway.domain.capability.header.HeaderPolicyComposer;
import com.cloud.gateway.domain.capability.resilience.TimeoutPolicyComposer;
import com.cloud.gateway.domain.ingress.access.AccessGrantedContext;
import com.cloud.gateway.domain.ingress.access.VerifiedApp;
import com.cloud.gateway.domain.ingress.access.subject.*;
import com.cloud.gateway.domain.ingress.snapshot.Revision;
import com.cloud.gateway.infrastructure.policy.CapabilityConfigCodecs;
import com.cloud.gateway.infrastructure.policy.config.CapabilityCodecConfiguration;
import com.cloud.gateway.infrastructure.policy.mapstruct.AbstractCapabilityNodeStorageMapStructMapperImpl;
import com.cloud.gateway.interfaces.facade.admin.definition.*;
import com.cloud.gateway.interfaces.facade.admin.definition.mapstruct.*;
import com.cloud.gateway.interfaces.rest.mapstruct.AbstractGatewayPolicyAdminRestMapStructMapperImpl;
import com.cloud.gateway.interfaces.webflux.GatewayRequestAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

class CapabilityPolicyConfigManagementIT {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Store store = new Store();
    private final Rollback rollback = new Rollback();
    private final CapabilityPolicyConfigValidator validator = BuiltInCapabilityCatalog.validator(
            new HeaderConfigRule(Set.of("host"), Set.of()));
    private final CapabilityConfigCodecs codecs = CapabilityCodecConfiguration.builtIns(
            validator, new AbstractCapabilityNodeStorageMapStructMapperImpl(), json);
    private final CapabilityPolicyConfigApiMapper mapper = new AbstractCapabilityPolicyConfigApiMapStructMapperImpl(codecs);
    private final CapabilityPolicyConfigCommandService service = new CapabilityPolicyConfigCommandService(store, rollback, new AccessPublishLock(new ImmediateLock()), validator);
    // Other definition use cases are not exercised by this controller/Facade contract fixture.
    private final DefaultAccessDefinitionCommandFacade facade = new DefaultAccessDefinitionCommandFacade(
            null, null, null, null, new AbstractAccessDefinitionApiMapStructMapperImpl(), service, mapper);
    private final GatewayAccessDefinitionCommandController controller = new GatewayAccessDefinitionCommandController(
            facade, new AbstractGatewayPolicyAdminRestMapStructMapperImpl());

    @Test
    void shouldSaveThroughHttpControllerAndReturnNextRevision() throws Exception {
        byte[] response = http().put().uri(ADMIN_POLICY_CONFIGURATION).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(document("r1", """
                        {"timeout":{"connect":"PT1S","read":"PT2S","total":"PT3S"},
                         "header-apply":{"trustedHeaders":["APP_ID"],"setHeaders":{},"removeHeaders":[],
                            "credentialForwarding":{"authorizationHeader":"STRIP","cookies":"STRIP_ALL","allowedCookieNames":[]}}}
                        """))
                .exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();
        assertTrue(json.readTree(response).toString().contains("\"sourceRevision\":\"r2\""));
        CapabilityBundle bundle = store.value.getBundles().get(new CapabilityBundleId("default"));
        assertEquals(Duration.ofSeconds(3), bundle.find(TimeoutPolicyComposer.INSTANCE.id(), TimeoutPolicyComposer.INSTANCE.configType()).orElseThrow().total());
        assertEquals(Set.of(com.cloud.framework.core.RequestHeader.APP_ID),
                bundle.find(HeaderPolicyComposer.INSTANCE.id(), HeaderPolicyComposer.INSTANCE.configType()).orElseThrow().trustedHeaders());
        assertEquals(1, store.writes);
        assertEquals(1, store.value.getBindings().size());
        // No publisher exists in this save chain; the captured runtime value is unchanged.
        assertTrue(store.published.getBundles().isEmpty());
    }

    @Test
    void shouldReplaceWholePolicyConfigAndRejectStaleRevision() throws Exception {
        facade.saveCapabilityPolicyConfig(command("r1", "{}"));
        assertEquals("r2", store.revision.value());
        ApplicationException conflict = assertThrows(ApplicationException.class,
                () -> facade.saveCapabilityPolicyConfig(command("r1", "{}")));
        assertEquals(ApplicationError.APP_POLICY_DEFINITION_UPDATE_CONFLICT.errorCode(), conflict.getErrorCode());
        SaveCapabilityPolicyConfigApiCommand empty = json.readValue(
                "{\"expectedSourceRevision\":\"r2\",\"policies\":{\"bundles\":{},\"bindings\":{}}}",
                SaveCapabilityPolicyConfigApiCommand.class);
        facade.saveCapabilityPolicyConfig(empty);
        assertTrue(store.value.getBundles().isEmpty());
        assertTrue(store.value.getBindings().isEmpty());
        assertEquals(2, store.writes);
    }

    @Test
    void shouldRejectInvalidCapabilityPayloadsBeforeWriting() {
        for (String node : java.util.List.of(
                "{\"unknown\":{}}",
                "{\"timeout\":{\"connect\":\"PT1S\",\"read\":\"PT2S\",\"total\":\"PT3S\",\"unknown\":true}}",
                "{\"header-apply\":{\"trustedHeaders\":{\"0\":\"APP_ID\"}}}",
                "{\"timeout\":null}")) {
            assertThrows(Exception.class, () -> facade.saveCapabilityPolicyConfig(command("r1", node)));
            assertEquals(0, store.writes);
        }
        assertThrows(FrameworkException.class, () -> facade.saveCapabilityPolicyConfig(command("r1", "{\"unknown\":{}}")));
    }

    @Test
    void shouldRejectNormalizedDuplicateBundleIdsAndDanglingBindings() throws Exception {
        SaveCapabilityPolicyConfigApiCommand duplicate = json.readValue("""
                {"expectedSourceRevision":"r1","policies":{
                  "bundles":{"same":{"bundleId":"same","capabilities":{}},
                             " same ":{"bundleId":"same","capabilities":{}}},"bindings":{}}}
                """, SaveCapabilityPolicyConfigApiCommand.class);
        assertThrows(FrameworkException.class, () -> facade.saveCapabilityPolicyConfig(duplicate));
        SaveCapabilityPolicyConfigApiCommand dangling = json.readValue("""
                {"expectedSourceRevision":"r1","policies":{"bundles":{},
                  "bindings":{"all":{"bindingId":"all","bundleId":"missing","tags":[]}}}}
                """, SaveCapabilityPolicyConfigApiCommand.class);
        assertThrows(DomainException.class, () -> facade.saveCapabilityPolicyConfig(dangling));
        assertEquals(0, store.writes);
    }

    @Test
    void shouldRetainHeaderDenyRulesAndDomainChecks() {
        assertThrows(DomainException.class, () -> facade.saveCapabilityPolicyConfig(command("r1", """
                {"header-apply":{"trustedHeaders":[],"setHeaders":{"Host":"bad"},"removeHeaders":[]}}
                """)));
        assertThrows(DomainException.class, () -> facade.saveCapabilityPolicyConfig(command("r1", """
                {"timeout":{"connect":"PT0S","read":"PT2S","total":"PT3S"}}
                """)));
        assertEquals(0, store.writes);
    }

    @Test
    void shouldRejectRollbackBlockedSave() {
        rollback.active = true;
        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> facade.saveCapabilityPolicyConfig(command("r1", "{}")));
        assertEquals(ApplicationError.APP_POLICY_ROLLBACK_IN_PROGRESS.errorCode(), exception.getErrorCode());
        assertEquals(0, store.writes);
    }

    @Test
    void shouldRejectMissingRequiredHttpFieldsAndUntrustedContext() {
        http().put().uri(ADMIN_POLICY_CONFIGURATION).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"expectedSourceRevision\":\"r1\"}")
                .exchange().expectStatus().isBadRequest();
        http().put().uri(ADMIN_POLICY_CONFIGURATION).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(document("", "{}"))
                .exchange().expectStatus().isBadRequest();
        WebTestClient.bindToController(controller)
                .controllerAdvice(new GatewayValidationExceptionHandler())
                .argumentResolvers(configurer -> configurer.addCustomResolver(new ClientRequestBodyArgumentResolver(
                        strictCodecs().getReaders(), ReactiveAdapterRegistry.getSharedInstance())))
                .build().put().uri(ADMIN_POLICY_CONFIGURATION).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(document("r1", "{}").replace("{\"expectedSourceRevision\"",
                        "{\"clientAppId\":\"forged\",\"userId\":\"forged\",\"sessionId\":\"forged\",\"expectedSourceRevision\""))
                .exchange().expectStatus().isBadRequest();
        assertEquals(0, store.writes);
    }

    @Test
    void shouldValidateDirectFacadeInvocationsThroughSpringProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MethodValidationPostProcessor.class);
            context.registerBean(DefaultAccessDefinitionCommandFacade.class, () -> facade);
            context.refresh();
            AccessDefinitionCommandFacade proxied = context.getBean(AccessDefinitionCommandFacade.class);
            assertThrows(jakarta.validation.ConstraintViolationException.class,
                    () -> proxied.saveCapabilityPolicyConfig(null));
            assertThrows(jakarta.validation.ConstraintViolationException.class,
                    () -> proxied.saveCapabilityPolicyConfig(new SaveCapabilityPolicyConfigApiCommand(" ", null)));
            assertEquals(0, store.writes);
        }
    }

    private static ServerCodecConfigurer strictCodecs() {
        org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder =
                new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
        new HeaderPolicyJsonCustomizer().customize(builder);
        ServerCodecConfigurer configurer = ServerCodecConfigurer.create();
        configurer.defaultCodecs().jackson2JsonDecoder(
                new org.springframework.http.codec.json.Jackson2JsonDecoder(builder.build()));
        return configurer;
    }

    @Test
    void shouldRejectDuplicateJsonKeysAndLegacyArraysBeforeWriting() {
        for (String body : java.util.List.of(
                document("r1", "{}").replace("\"all\":{", "\"all\":{\"bindingId\":\"all\",\"bundleId\":\"default\",\"tags\":[]},\"all\":{"),
                document("r1", "{}").replace("\"bindings\":{", "\"bindings\":{},\"bindings\":{"),
                document("r1", "{}").replace("\"capabilities\":{}", "\"capabilities\":{\"timeout\":{},\"timeout\":{}}"),
                document("r1", "{}").replace("\"bindings\":{\"all\":{\"bindingId\":\"all\",\"bundleId\":\"default\",\"tags\":[]}}",
                        "\"bindings\":[]"))) {
            http().put().uri(ADMIN_POLICY_CONFIGURATION).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body).exchange().expectStatus().isBadRequest();
            assertEquals(0, store.writes);
        }
    }

    @Test
    void shouldRejectBindingKeyMismatchAndNormalizedCollisions() throws Exception {
        String valid = document("r1", "{}");
        String mismatch = valid.replace("\"all\":{", "\"other\":{");
        String duplicate = valid.replace("\"all\":{", "\" all \":{\"bindingId\":\"all\",\"bundleId\":\"default\",\"tags\":[]},\"all\":{");
        for (String body : java.util.List.of(mismatch, duplicate)) {
            SaveCapabilityPolicyConfigApiCommand command = json.readValue(body, SaveCapabilityPolicyConfigApiCommand.class);
            assertThrows(FrameworkException.class, () -> facade.saveCapabilityPolicyConfig(command));
            assertEquals(0, store.writes);
        }
    }

    private WebTestClient http() {
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new GatewayValidationExceptionHandler())
                .argumentResolvers(configurer -> configurer.addCustomResolver(new ClientRequestBodyArgumentResolver(
                        strictCodecs().getReaders(), ReactiveAdapterRegistry.getSharedInstance())))
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(GatewayRequestAttributes.TRUSTED_CONTEXT,
                            new AccessGrantedContext(new VerifiedApp(new AppId("management"), "WEB"), null,
                                    new AuthenticatedSubject(SubjectType.HOST_SESSION, new SubjectId("admin"),
                                            new SessionId("session"), "issuer", Set.of("gateway"), Set.of("admin"),
                                            Instant.parse("2099-01-01T00:00:00Z"))));
                    return chain.filter(exchange);
                }).build();
    }

    private SaveCapabilityPolicyConfigApiCommand command(String revision, String nodes) throws Exception {
        return json.readValue(document(revision, nodes), SaveCapabilityPolicyConfigApiCommand.class);
    }

    private String document(String revision, String nodes) {
        return """
                {"expectedSourceRevision":"%s","policies":{
                  "bundles":{"default":{"bundleId":"default","capabilities":%s}},
                  "bindings":{"all":{"bindingId":"all","bundleId":"default","tags":[]}}}}
                """.formatted(revision, nodes);
    }

    private static final class Store implements WritableCapabilityPolicyConfigDefinitionRepository {
        private CapabilityPolicyConfig value = CapabilityPolicyConfig.empty();
        private final CapabilityPolicyConfig published = value;
        private Revision revision = new Revision("r1");
        private int writes;
        public RevisionedAccessDefinition<CapabilityPolicyConfig> load() {
            return new RevisionedAccessDefinition<>(value, revision);
        }
        public Revision compareAndSet(Revision expected, CapabilityPolicyConfig next) {
            if (!revision.equals(expected)) { return null; }
            value = next;
            revision = new Revision("r" + (++writes + 1));
            return revision;
        }
    }

    private static final class Rollback implements AccessRollbackState {
        private boolean active;
        public boolean isActive() { return active; }
        public void activate() { active = true; }
        public void clear() { active = false; }
    }

    private static final class ImmediateLock implements LockExecutor {
        public <T> Optional<T> execute(LockContext context, Callable<T> action) throws Exception {
            return Optional.ofNullable(action.call());
        }
    }
}
