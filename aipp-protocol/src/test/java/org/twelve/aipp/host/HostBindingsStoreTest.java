package org.twelve.aipp.host;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HostBindingsStoreTest {

    @Test
    void mergeByTopLevelKeys() {
        HostBindingsStore store = new HostBindingsStore();
        store.merge(Map.of(
                "env", "production",
                "host_base_url", "http://127.0.0.1:8090"
        ));
        store.merge(Map.of("env", "staging"));

        assertThat(store.getString("env", "")).isEqualTo("staging");
        assertThat(store.getString("host_base_url", "")).isEqualTo("http://127.0.0.1:8090");
    }

    @Test
    void notifiesOnChange() {
        HostBindingsStore store = new HostBindingsStore();
        AtomicInteger calls = new AtomicInteger();
        store.setOnChange(bindings -> calls.incrementAndGet());
        store.merge(Map.of("env", "production"));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void supportPutGet() {
        HostBindingsStore store = new HostBindingsStore();
        Map<String, Object> put = HostBindingsSupport.put(store, new LinkedHashMap<>(Map.of(
                "host_id", "worldone",
                "env", "production"
        )));
        assertThat(put).containsEntry("ok", true);
        Map<String, Object> get = HostBindingsSupport.get(store);
        assertThat(get).containsEntry("ok", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> bindings = (Map<String, Object>) get.get("bindings");
        assertThat(bindings).containsEntry("env", "production");
    }
}
