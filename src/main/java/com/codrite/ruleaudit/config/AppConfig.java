package com.codrite.ruleaudit.config;

import com.codrite.ruleaudit.eval.RuleEvaluator;
import com.codrite.ruleaudit.json.JsonContextFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * General application configuration for shared infrastructure components.
 */
@Configuration
public class AppConfig {

    /**
     * Provides the evaluation engine for SpEL rules.
     */
    @Bean
    public RuleEvaluator ruleEvaluator() {
        return new RuleEvaluator();
    }

    /**
     * Provides the factory for transforming JSON into evaluation contexts.
     */
    @Bean
    public JsonContextFactory jsonContextFactory() {
        return new JsonContextFactory();
    }

    /**
     * Configures the primary Jackson {@link ObjectMapper} for the application.
     * <p>
     * Includes support for Java 8 Date/Time types and ensures ISO-8601 formatting 
     * for timestamps.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
