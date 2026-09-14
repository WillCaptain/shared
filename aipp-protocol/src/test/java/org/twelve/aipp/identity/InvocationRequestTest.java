package org.twelve.aipp.identity;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class InvocationRequestTest {
    private InvocationRequest request(String target, byte[] body) {
        return new InvocationRequest("sample-app", "sample_update", "POST", target, body);
    }

    @Test void snapshotsBytesAndPinsStandardDigestVector() {
        byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
        var request = request("/api/tools/sample_update", input);
        input[0] = 'x'; request.body()[0] = 'y';
        assertThat(new String(request.body(), StandardCharsets.UTF_8)).isEqualTo("abc");
        assertThat(request.bodySha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(request("/api/tools/sample_update", new byte[0]).bodySha256())
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test void preservesExactQueryAndDoesNotNormalizeJson() {
        String target = "/api/tools/sample_update?x=a%20b&x=a+b";
        var one = request(target, "{\"x\":1}".getBytes(StandardCharsets.UTF_8));
        var two = request(target, "{ \"x\": 1 }".getBytes(StandardCharsets.UTF_8));
        assertThat(one.requestTarget()).isEqualTo(target);
        assertThat(one.bodySha256()).isNotEqualTo(two.bodySha256());
    }

    @Test void rejectsAmbiguousTargetsAndInvalidScope() {
        for (String target : List.of("https://example.test/a", "//example.test/a", "/a#fragment", "/a/../b",
                "/a/%2e%2e/b", "/a%2fb", "/a%5cb", "/a%252fb", "/a b", "/你好"))
            assertThatThrownBy(() -> request(target, new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvocationRequest("sample-app", "sample_update", "post", "/a", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvocationRequest("https://host", "sample_update", "POST", "/a", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("/a", null)).isInstanceOf(NullPointerException.class);
    }

    @Test void diagnosticStringDoesNotExposeRequestData() {
        var request = request("/api/tools/sample_update?secret=private", "credential".getBytes(StandardCharsets.UTF_8));
        assertThat(request.toString()).isEqualTo("InvocationRequest[redacted]");
    }
}
