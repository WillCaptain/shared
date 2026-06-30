package org.twelve.aipp.host.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.twelve.aipp.host.AippHostLifecycle;

/**
 * Starts and stops the standard AIPP ↔ Host attach loop on Spring lifecycle events.
 * Registered as a bean by {@link AippHostAttachAutoConfiguration}.
 */
public final class AippHostAttachLifecycle implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AippHostAttachLifecycle.class);

    private final AippHostLifecycle lifecycle;
    private final AippHostAttachProperties properties;

    public AippHostAttachLifecycle(AippHostLifecycle lifecycle, AippHostAttachProperties properties) {
        this.lifecycle = lifecycle;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.getAttach().isEnabled()) {
            log.info("[AippHostAttach] attach loop disabled via aipp.attach.enabled=false");
            return;
        }
        if (!lifecycle.configured()) {
            log.info("[AippHostAttach] Host lifecycle not fully configured; skipping attach loop.");
            return;
        }
        log.info("[AippHostAttach] Starting attach loop for '{}' → {} (base_url={}, instance={})",
                lifecycle.appId(), lifecycle.hostBaseUrl(), lifecycle.selfBaseUrl(),
                lifecycle.instanceId());
        var attach = properties.getAttach();
        lifecycle.startAttachLoop(
                attach.getInitialAttempts(), attach.getRetryDelay(), attach.getRefreshInterval());
    }

    @Override
    public void destroy() {
        lifecycle.stop();
        log.debug("[AippHostAttach] Stopped attach loop (instance={})", lifecycle.instanceId());
    }
}
