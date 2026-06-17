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

/** Plain (non-annotated) core components exposed as beans. */
@Configuration
public class AppConfig {

    @Bean
    public RuleEvaluator ruleEvaluator() {
        return new RuleEvaluator();
    }

    @Bean
    public JsonContextFactory jsonContextFactory() {
        return new JsonContextFactory();
    }

    /**
     * With spring-web, Spring auto-configures an ObjectMapper.
     * We mark ours as @Primary to ensure it's used for our records.
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
