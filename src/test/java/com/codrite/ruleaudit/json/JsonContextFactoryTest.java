package com.codrite.ruleaudit.json;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonContextFactoryTest {

    @Test
    void toRoot_ShouldParseFlatObject() {
        JsonContextFactory factory = new JsonContextFactory();
        String json = "{\"amount\":5000,\"region\":\"EU\"}";
        Map<String, Object> result = factory.toRoot(json);

        assertThat(result).containsEntry("amount", 5000);
        assertThat(result).containsEntry("region", "EU");
    }

    @Test
    void toRoot_ShouldParseNestedObject() {
        JsonContextFactory factory = new JsonContextFactory();
        String json = "{\"order\":{\"total\":50}}";
        Map<String, Object> result = factory.toRoot(json);

        assertThat(result).containsKey("order");
        Map<String, Object> orderMap = (Map<String, Object>) result.get("order");
        assertThat(orderMap).containsEntry("total", 50);
    }

    @Test
    void toRoot_ShouldParseArray() {
        JsonContextFactory factory = new JsonContextFactory();
        String json = "{\"tags\":[\"a\",\"b\"]}";
        Map<String, Object> result = factory.toRoot(json);

        assertThat(result).containsKey("tags");
        List<Object> tags = (List<Object>) result.get("tags");
        assertThat(tags).containsExactly("a", "b");
    }

    @Test
    void toRoot_ShouldThrowOnMalformedJson() {
        JsonContextFactory factory = new JsonContextFactory();
        String json = "{not json";

        assertThatThrownBy(() -> factory.toRoot(json))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void toRoot_ShouldThrowOnNonObjectTopLevel() {
        JsonContextFactory factory = new JsonContextFactory();

        assertThatThrownBy(() -> factory.toRoot("[1,2,3]"))
                .isInstanceOf(JsonParseException.class);

        assertThatThrownBy(() -> factory.toRoot("42"))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void toRoot_ByteVsStringShouldBeEquivalent() {
        JsonContextFactory factory = new JsonContextFactory();
        String json = "{\"key\":\"value\"}";
        Map<String, Object> stringResult = factory.toRoot(json);
        Map<String, Object> byteResult = factory.toRoot(json.getBytes(StandardCharsets.UTF_8));

        assertThat(byteResult).isEqualTo(stringResult);
    }
}
