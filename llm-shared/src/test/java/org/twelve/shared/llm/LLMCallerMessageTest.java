package org.twelve.shared.llm;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the additive multimodal-content branch in {@link LLMCaller#messageToJson}:
 * String/null content must serialize exactly as before; List content becomes a
 * JSON content-block array.
 */
class LLMCallerMessageTest {

    @Test
    void stringContentSerializationUnchanged() {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", "call_1");
        msg.put("name", "terminal_run");
        msg.put("content", "{\"ok\":true,\"stdout\":\"hi\"}");

        assertThat(LLMCaller.messageToJson(msg)).isEqualTo(
                "{\"role\":\"tool\",\"content\":\"{\\\"ok\\\":true,\\\"stdout\\\":\\\"hi\\\"}\","
                        + "\"tool_call_id\":\"call_1\",\"name\":\"terminal_run\"}");
    }

    @Test
    void nullContentSerializesAsNull() {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        msg.put("content", null);
        assertThat(LLMCaller.messageToJson(msg)).isEqualTo("{\"role\":\"assistant\",\"content\":null}");
    }

    @Test
    void listContentBecomesBlockArray() {
        Map<String, Object> textBlock = new LinkedHashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", "screenshot:");

        Map<String, Object> imageBlock = new LinkedHashMap<>();
        imageBlock.put("type", "image_url");
        imageBlock.put("image_url", Map.of("url", "data:image/png;base64,AAAA"));

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", List.of(textBlock, imageBlock));

        assertThat(LLMCaller.messageToJson(msg)).isEqualTo(
                "{\"role\":\"user\",\"content\":["
                        + "{\"type\":\"text\",\"text\":\"screenshot:\"},"
                        + "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,AAAA\"}}"
                        + "]}");
    }
}
