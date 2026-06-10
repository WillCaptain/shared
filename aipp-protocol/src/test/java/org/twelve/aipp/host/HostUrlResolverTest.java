package org.twelve.aipp.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HostUrlResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsToLocalHost8090() {
        assertThat(HostUrlResolver.resolve("")).isEqualTo("http://127.0.0.1:8090");
    }

    @Test
    void normalizesBareHostPort() {
        assertThat(HostUrlResolver.normalizeBaseUrl("127.0.0.1:8090"))
                .isEqualTo("http://127.0.0.1:8090");
    }

    @Test
    void loadsFromGlobalConfigFile() throws Exception {
        Path cfg = tempDir.resolve("host.json");
        Files.writeString(cfg, """
                {"host_base_url":"http://my-host:9000"}
                """);
        System.setProperty("aipp.host.config", cfg.toString());
        try {
            assertThat(HostUrlResolver.resolve("")).isEqualTo("http://my-host:9000");
        } finally {
            System.clearProperty("aipp.host.config");
        }
    }

    @Test
    void eventCallbackUrlIsDerived() {
        HostBindingsStore store = new HostBindingsStore();
        store.merge(java.util.Map.of("env", "production"));
        assertThat(HostUrlResolver.eventCallbackBaseUrl(store, "decision-reactor", ""))
                .isEqualTo("http://127.0.0.1:8090/api/host/event-callbacks/decision-reactor");
    }
}
