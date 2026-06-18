package com.codrite.ruleaudit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer wiring for the audit-writer path.
 * <p>
 * Provides a <em>batch</em> listener container factory so the audit consumer
 * receives a whole poll's worth of records at once. Those records are then
 * persisted with a single MongoDB {@code bulkWrite}, amortising one
 * {@code w:majority} replication round-trip over the entire chunk instead of
 * paying it per record.
 * <p>
 * Chunk sizing is controlled by the consumer fetch properties in
 * {@code application.yml} ({@code max.poll.records}, {@code fetch.min.bytes},
 * {@code fetch.max.wait.ms}).
 */
@Configuration
public class KafkaConsumerConfig {

    /**
     * Batch listener factory dedicated to the audit consumer.
     * <p>
     * Reuses Spring Boot's auto-configured {@link ConsumerFactory} (which already
     * carries the {@code spring.kafka.consumer.*} settings), enables batch
     * delivery, and keeps manual acknowledgment so the whole batch is committed
     * only after the bulk write succeeds.
     * <p>
     * A bounded {@link DefaultErrorHandler} (5 retries, 1s apart) prevents a
     * persistent failure from stalling the partition indefinitely; on exhaustion
     * the batch is logged and skipped. Redelivery is safe because the bulk
     * upserts are idempotent on {@code _id}.
     *
     * @param consumerFactory Spring-managed consumer factory.
     * @return a batch-mode listener container factory.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> auditBatchListenerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 5L)));
        return factory;
    }
}
