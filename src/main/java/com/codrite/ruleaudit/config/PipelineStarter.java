package com.codrite.ruleaudit.config;

import com.codrite.ruleaudit.rules.RuleLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the startup sequence of the application.
 * <p>
 * To ensure that the Kafka Streams topology doesn't process records without 
 * valid rules, this component:
 * <ol>
 *     <li>Triggers an initial rule load from the persistent store into memory.</li>
 *     <li>Manually starts the Kafka Streams factory (which has {@code auto-startup} disabled in configuration).</li>
 * </ol>
 */
@Slf4j
@Component
@Order(1)
public class PipelineStarter implements ApplicationRunner {

    private final RuleLoader ruleLoader;
    private final StreamsBuilderFactoryBean streamsFactory;

    public PipelineStarter(RuleLoader ruleLoader, StreamsBuilderFactoryBean streamsFactory) {
        this.ruleLoader = ruleLoader;
        this.streamsFactory = streamsFactory;
    }

    /**
     * Executes the startup sequence after the Spring context is fully initialized.
     * @param args Application arguments.
     */
    @Override
    public void run(ApplicationArguments args) {
        // Step 1: Initialize the local rule cache
        int n = ruleLoader.reload();
        log.info("Loaded {} rules; starting Kafka Streams", n);
        
        // Step 2: Boot the stream processing topology
        streamsFactory.start();
    }
}
