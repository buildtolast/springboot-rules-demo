package com.codrite.ruleaudit.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class TopicsConfig {

    @Value("${app.partitions}")
    private int partitions;

    @Value("${app.replication-factor}")
    private short replicationFactor;

    @Bean
    public NewTopic sourceTopic(@Value("${app.topics.source}") String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic targetTopic(@Value("${app.topics.target}") String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic auditTopic(@Value("${app.topics.audit}") String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }

    @Bean
    public NewTopic auditDltTopic(@Value("${app.topics.audit-dlt}") String name) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }
}
