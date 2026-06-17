package com.codrite.ruleaudit.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Drains the audit topic and upserts each record into Mongo (idempotent on _id).
 * Manual ack: the offset is committed only after the Mongo write succeeds.
 */
@Component
public class AuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper mapper;

    public AuditConsumer(MongoTemplate mongoTemplate, ObjectMapper mapper) {
        this.mongoTemplate = mongoTemplate;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${app.topics.audit}", groupId = "audit-writer")
    public void onAudit(String json, Acknowledgment ack) throws Exception {
        try {
            AuditRecord record = mapper.readValue(json, AuditRecord.class);
            mongoTemplate.save(record); // _id present -> idempotent upsert
            ack.acknowledge();
            log.debug("Persisted audit {} ({})", record.auditId(), record.auditType());
        } catch (Exception e) {
            log.error("Failed to process audit record: {}", json, e);
            throw e;
        }
    }
}
