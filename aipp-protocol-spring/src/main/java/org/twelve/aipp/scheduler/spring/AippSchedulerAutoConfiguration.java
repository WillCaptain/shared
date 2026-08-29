package org.twelve.aipp.scheduler.spring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.twelve.aipp.scheduler.*;

@AutoConfiguration
@ConditionalOnClass(AippScheduleRegistrar.class)
public class AippSchedulerAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    DefaultAippScheduleRegistrar aippScheduleRegistrar(ObjectProvider<AippScheduleHandler> handlers) {
        DefaultAippScheduleRegistrar registrar = new DefaultAippScheduleRegistrar();
        handlers.orderedStream().forEach(registrar::register);
        return registrar;
    }

    @Bean @ConditionalOnMissingBean
    AippScheduleController aippScheduleController(DefaultAippScheduleRegistrar registrar, Environment env) {
        String appId = env.getProperty("aipp.app-id", env.getProperty("spring.application.name", ""));
        return new AippScheduleController(registrar, appId);
    }
}
