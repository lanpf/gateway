package com.cloud.gateway.infrastructure.policy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;

/** Only for Spring's untyped property maps: indexed objects become arrays when the registered type requires a collection. */
final class IndexedPropertyCollections extends DeserializationProblemHandler {
    @Override
    public Object handleUnexpectedToken(DeserializationContext context, JavaType targetType, JsonToken token,
            JsonParser parser, String message) throws IOException {
        if (!targetType.isCollectionLikeType() || token != JsonToken.START_OBJECT) {
            return NOT_HANDLED;
        }
        JsonNode object = parser.readValueAsTree();
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < object.size(); index++) {
            JsonNode value = object.get(Integer.toString(index));
            if (value == null) {
                throw JsonMappingException.from(parser, "Property collection indices must be contiguous from zero");
            }
            array.add(value);
        }
        return context.readTreeAsValue(array, targetType);
    }
}
