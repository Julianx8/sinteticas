package com.sinteticas.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Un único {@link Clock} inyectable: el código nunca llama a {@code Instant.now()} directamente,
 * así los tests pueden fijar la hora.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(AppProperties appProperties) {
        return Clock.system(appProperties.zoneId());
    }

}
