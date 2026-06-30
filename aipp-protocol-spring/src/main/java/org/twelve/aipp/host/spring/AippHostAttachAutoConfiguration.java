package org.twelve.aipp.host.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.twelve.aipp.host.AippHostLifecycle;

/**
 * Standard Spring Boot wiring for the AIPP ↔ Host attach loop.
 *
 * <p>Activate by adding {@code aipp-protocol-spring} to the classpath and setting:
 * <pre>
 * aipp.host.base-url: http://127.0.0.1:8090
 * aipp.self-base-url: http://localhost:${server.port}
 * aipp.app-id: my-app   # optional; defaults to spring.application.name
 * </pre>
 *
 * <p>See aipp-protocol {@code spec/host-lifecycle.md}.
 */
@AutoConfiguration
@ConditionalOnClass(AippHostLifecycle.class)
@EnableConfigurationProperties(AippHostAttachProperties.class)
@ConditionalOnProperty(prefix = "aipp.host", name = "base-url")
public class AippHostAttachAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AippHostLifecycle aippHostLifecycle(AippHostAttachProperties properties, Environment env) {
        String appId = properties.getAppId();
        if (appId.isBlank()) {
            appId = env.getProperty("spring.application.name", "");
        }
        return new AippHostLifecycle(
                properties.getHost().getBaseUrl(), appId, properties.getSelfBaseUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public AippHostAttachLifecycle aippHostAttachLifecycle(
            AippHostLifecycle lifecycle, AippHostAttachProperties properties) {
        return new AippHostAttachLifecycle(lifecycle, properties);
    }
}
