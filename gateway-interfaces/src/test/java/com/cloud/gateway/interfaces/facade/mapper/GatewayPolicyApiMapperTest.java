package com.cloud.gateway.interfaces.facade.mapper;

import com.cloud.gateway.api.enums.AppStatusApiEnum;
import com.cloud.gateway.api.policy.ReleaseSnapshotApiCommand;
import com.cloud.gateway.api.policy.SnapshotReleaseApiCommandOutput;
import com.cloud.gateway.api.policy.RollbackSnapshotApiCommand;
import com.cloud.gateway.api.policy.SaveAppApiCommand;
import com.cloud.gateway.application.policy.PolicyPublicationOutput;
import com.cloud.gateway.application.policy.SaveAppPolicyCommand;
import com.cloud.gateway.domain.common.DomainException;
import com.cloud.gateway.domain.app.AppId;
import com.cloud.gateway.interfaces.facade.mapper.mapstruct.GatewayPolicyApiMapStructMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class GatewayPolicyApiMapperTest {
    private final GatewayPolicyApiMapper mapper = Mappers.getMapper(GatewayPolicyApiMapStructMapper.class);

    @Test
    void shouldMapPublicationAndRollbackFieldsThroughGeneratedImplementation() {
        SnapshotReleaseApiCommandOutput output = mapper.toApiOutput(new PolicyPublicationOutput(17, "content-hash"));
        assertEquals(17, output.snapshotVersion());
        assertEquals("content-hash", output.contentHash());
        assertEquals(11, mapper.toCommand(new RollbackSnapshotApiCommand(11)).targetSnapshotVersion());
        assertNotNull(mapper.toCommand(new ReleaseSnapshotApiCommand()));
    }

    @Test
    void shouldPreserveAppCommandRevisionAndExplicitDomainConstruction() {
        SaveAppPolicyCommand command = mapper.toCommand(new SaveAppApiCommand("revision", "app",
                AppStatusApiEnum.DISABLED, "WEB", Set.of("https://example.test")));
        assertEquals("revision", command.expectedSourceRevision());
        assertEquals(new AppId("app"), command.policy().appId());
        assertEquals(PolicyStatus.DISABLED, command.policy().status());
        assertEquals("WEB", command.policy().platform());
        assertEquals(Set.of("https://example.test"), command.policy().allowedOrigins());
        assertThrows(DomainException.class, () -> mapper.toCommand(new SaveAppApiCommand(
                "revision", "app", AppStatusApiEnum.ENABLED, "WEB", Set.of("https://example.test/path"))));
    }

    @Test
    void shouldExposeExactlyOneGeneratedBeanThroughIndependentContract() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.scan("com.cloud.gateway.interfaces.facade.mapper.mapstruct");
            context.refresh();
            assertEquals(1, context.getBeansOfType(GatewayPolicyApiMapper.class).size());
            GatewayPolicyApiMapper bean = context.getBean(GatewayPolicyApiMapper.class);
            assertEquals(9, bean.toCommand(new RollbackSnapshotApiCommand(9)).targetSnapshotVersion());
        }
    }
}
