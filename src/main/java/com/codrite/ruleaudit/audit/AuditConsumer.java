package com.codrite.ruleaudit.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer that persists evaluation results to MongoDB.
 * <p>
 * This consumer listens to the audit topic where the Kafka Streams topology
 * sends detailed records of every event evaluation. It ensures that all 
 * activities are searchable and auditable in a persistent database.
 * <p>
 * Features:
 * <ul>
 *     <li>Manual Acknowledgment: Ensures records are only cleared from Kafka after successful persistence.</li>
 *     <li>Idempotency: Uses the deterministic auditId as the MongoDB _id to prevent duplicates.</li>
 * </ul>
 */
@Slf4j
@Component
public class AuditConsumer {

    private final AuditRepository auditRepository;
    private final ObjectMapper mapper;

    /**
     * Constructor for AuditConsumer.
     * @param auditRepository Reactive repository for persistence.
     * @param mapper Jackson ObjectMapper for deserialization.
     */
    public AuditConsumer(AuditRepository auditRepository, ObjectMapper mapper) {
        this.auditRepository = auditRepository;
        this.mapper = mapper;
    }

    /**
     * Listener method for the audit topic.
     * 
     * @param json The serialized AuditRecord from Kafka.
     * @param ack  Manual acknowledgment handle.
     */
    @KafkaListener(topics = "${app.topics.audit}", groupId = "audit-writer")
    public void onAudit(String json, Acknowledgment ack) {
        try {
            // Deserialized the raw JSON back into a structured AuditRecord
            AuditRecord record = mapper.readValue(json, AuditRecord.class);
            
            // Async save using reactive repository
            auditRepository.save(record)
                .subscribe(
                    saved -> {
                        // Confirm processing to Kafka ONLY after successful save
                        ack.acknowledge();
                        log.debug("Persisted audit {} ({})", saved.auditId(), saved.auditType());
                    },
                    error -> {
                        log.error("Failed to persist audit record: {}", json, error);
                        // Note: Acknowledgment is NOT called here, so Kafka will re-deliver 
                        // based on the configured error handling/retry strategy.
                    }
                );
        } catch (Exception e) {
            log.error("Failed to parse audit record: {}", json, e);
            // Parsing errors are usually poison pills, but in this system we might 
            // want to avoid acknowledging to investigate or let Kafka retry.
        }
    }
}
