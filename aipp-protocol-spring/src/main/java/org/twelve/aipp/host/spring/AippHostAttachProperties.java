package org.twelve.aipp.host.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.twelve.aipp.host.AippHostLifecycle;

import java.time.Duration;

/**
 * Standard configuration for the AIPP ↔ Host attach loop.
 * See aipp-protocol {@code spec/host-lifecycle.md}.
 */
@ConfigurationProperties(prefix = "aipp")
public class AippHostAttachProperties {

    /** Kebab-case id; defaults to {@code spring.application.name} when blank. */
    private String appId = "";

    private final Host host = new Host();

    /** Externally reachable base URL the Host uses to reach this AIPP. */
    private String selfBaseUrl = "";

    private final Attach attach = new Attach();

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId == null ? "" : appId; }

    public Host getHost() { return host; }

    public String getSelfBaseUrl() { return selfBaseUrl; }
    public void setSelfBaseUrl(String selfBaseUrl) {
        this.selfBaseUrl = selfBaseUrl == null ? "" : selfBaseUrl;
    }

    public Attach getAttach() { return attach; }

    public static final class Host {
        /** Host (world-one) base URL. When blank, auto-configuration is skipped. */
        private String baseUrl = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
    }

    public static final class Attach {
        /** Set {@code false} to disable the attach loop (e.g. tests). */
        private boolean enabled = true;

        private int initialAttempts = AippHostLifecycle.DEFAULT_INITIAL_ATTEMPTS;
        private Duration retryDelay = AippHostLifecycle.DEFAULT_RETRY_DELAY;
        private Duration refreshInterval = AippHostLifecycle.DEFAULT_REFRESH_INTERVAL;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getInitialAttempts() { return initialAttempts; }
        public void setInitialAttempts(int initialAttempts) { this.initialAttempts = initialAttempts; }

        public Duration getRetryDelay() { return retryDelay; }
        public void setRetryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay == null ? AippHostLifecycle.DEFAULT_RETRY_DELAY : retryDelay;
        }

        public Duration getRefreshInterval() { return refreshInterval; }
        public void setRefreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval == null
                    ? AippHostLifecycle.DEFAULT_REFRESH_INTERVAL : refreshInterval;
        }
    }
}
