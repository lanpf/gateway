package com.cloud.gateway.infrastructure.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloud.gateway.infrastructure.common.InfrastructureException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyYamlCodecTest {
    private final TestPolicyYamlCodec codec =
            new TestPolicyYamlCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void shouldDecodeClassAndApplyMapping() {
        assertEquals("EXPECTED", codec.decodeValue("value: expected"));
    }

    @Test
    void shouldDecodeClassWithoutMapping() {
        assertEquals(new SamplePayload("expected"), codec.decodePayload("value: expected"));
    }

    @Test
    void shouldDecodeTypeReferenceWithoutMapping() {
        assertEquals(Map.of("key", "value"), codec.decodeMap("key: value"));
    }

    @Test
    void shouldDecodeTypeReferenceAndApplyMapping() {
        assertEquals("value", codec.decodeMapValue("key: value"));
    }

    @Test
    void shouldEncodeValue() {
        assertEquals("---\nvalue: \"expected\"\n", codec.encodePayload(new SamplePayload("expected")));
    }

    @Test
    void shouldRejectUnknownProperties() {
        assertThrows(InfrastructureException.class,
                () -> codec.decodeValue("value: expected\nunknown: rejected"));
    }

    private static final class TestPolicyYamlCodec extends AbstractPolicyYamlCodec {
        private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() { };

        private TestPolicyYamlCodec(ObjectMapper source) {
            super(source);
        }

        private String decodeValue(String content) {
            return decode(content, SamplePayload.class, payload -> payload.value().toUpperCase());
        }

        private SamplePayload decodePayload(String content) {
            return decode(content, SamplePayload.class);
        }

        private Map<String, String> decodeMap(String content) {
            return decode(content, MAP_TYPE);
        }

        private String decodeMapValue(String content) {
            return decode(content, MAP_TYPE, values -> values.get("key"));
        }

        private String encodePayload(SamplePayload payload) {
            return encode(payload);
        }
    }

    private record SamplePayload(String value) { }
}
