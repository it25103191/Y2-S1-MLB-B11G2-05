package com.safari.tms.config;

import com.safari.tms.service.ReferenceGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hands Spring the one shared {@link ReferenceGenerator} (a classic Singleton), so services keep
 * receiving it by constructor injection instead of calling {@code getInstance()} themselves.
 */
@Configuration
public class ReferenceGeneratorConfig {

    @Bean
    public ReferenceGenerator referenceGenerator() {
        return ReferenceGenerator.getInstance();
    }
}
