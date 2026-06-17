package com.codrite.ruleaudit.config;

import com.codrite.ruleaudit.rules.RuleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Component;

/**
 * Loads rules into the cache, then starts the Spring-managed Kafka Streams
 * (auto-startup is disabled so rules are guaranteed present before processing).
 */
@Component
@Order(1)
public class PipelineStarter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineStarter.class);

    private final RuleLoader ruleLoader;
    private final StreamsBuilderFactoryBean streamsFactory;

    public PipelineStarter(RuleLoader ruleLoader, StreamsBuilderFactoryBean streamsFactory) {
        this.ruleLoader = ruleLoader;
        this.streamsFactory = streamsFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        int n = ruleLoader.reload();
        log.info("Loaded {} rules; starting Kafka Streams", n);
        streamsFactory.start();
    }
}
