package com.codrite.ruleaudit.config;

import org.apache.kafka.clients.admin.NewTopic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Topics configuration.
 * <p>
 * This class automatically creates the required Kafka topics if they do not
 * already exist. The number of partitions and replication factor are 
 * configurable via {@code application.yml}.
 */
@Slf4j
@Configuration
public class TopicsConfig {

    @Value("${app.partitions}")
    private int partitions;

    @Value("${app.replication-factor}")
    private short replicationFactor;

    /**
     * The input topic where raw events are published.
     */
    @Bean
    public NewTopic sourceTopic(@Value("${app.topics.source}") String name) {
        log.info("Configuring source topic: {} (partitions={}, replicas={})", name, partitions, replicationFactor);
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }

    /**
     * The output topic where matched events are routed.
     */
    @Bean
    public NewTopic targetTopic(@Value("${app.topics.target}") String name) {
        log.info("Configuring target topic: {} (partitions={}, replicas={})", name, partitions, replicationFactor);
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }

    /**
     * The audit topic where evaluation summaries and metadata are sent.
     */
    @Bean
    public NewTopic auditTopic(@Value("${app.topics.audit}") String name) {
        log.info("Configuring audit topic: {} (partitions={}, replicas={})", name, partitions, replicationFactor);
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }

    /**
     * Dead Letter Topic (DLT) for the audit consumer.
     */
    @Bean
    public NewTopic auditDltTopic(@Value("${app.topics.audit-dlt}") String name) {
        log.info("Configuring audit DLT topic: {} (partitions={}, replicas={})", name, partitions, replicationFactor);
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }
}
