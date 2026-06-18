package com.codrite.ruleaudit.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.bulk.BulkWriteResult;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer that persists evaluation results to MongoDB in batches.
 * <p>
 * This consumer listens to the audit topic where the Kafka Streams topology
 * sends detailed records of every event evaluation. It receives a whole poll's
 * worth of records at once (see {@code auditBatchListenerFactory}) and writes
 * them with a single MongoDB {@code bulkWrite}, so one {@code w:majority}
 * replication round-trip is amortised over the entire chunk rather than paid per
 * record.
 * <p>
 * Features:
 * <ul>
 *     <li>Batch persistence: one bulk upsert per poll, dramatically reducing the
 *         per-record write-concern wait.</li>
 *     <li>Manual Acknowledgment: the batch is committed to Kafka only after the
 *         bulk write succeeds.</li>
 *     <li>Idempotency: uses the deterministic auditId as the MongoDB {@code _id},
 *         so redelivery of an un-acked batch re-upserts without creating
 *         duplicates.</li>
 * </ul>
 */
@Slf4j
@Component
public class AuditConsumer {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper mapper;

    /**
     * Constructor for AuditConsumer.
     *
     * @param mongoTemplate Blocking Mongo template (carries the MAJORITY write concern).
     * @param mapper        Jackson ObjectMapper for deserialization.
     */
    public AuditConsumer(MongoTemplate mongoTemplate, ObjectMapper mapper) {
        this.mongoTemplate = mongoTemplate;
        this.mapper = mapper;
    }

    /**
     * Batch listener for the audit topic.
     * <p>
     * Unparseable records are logged and skipped (they would otherwise poison the
     * whole batch); the remaining records are persisted in a single unordered
     * bulk upsert. A Mongo failure propagates so the container's error handler
     * redelivers the batch — safe because the upserts are idempotent on
     * {@code _id}.
     *
     * @param messages The serialized AuditRecords from one poll.
     * @param ack      Manual acknowledgment handle for the batch.
     */
    @KafkaListener(topics = "${app.topics.audit}", groupId = "audit-writer",
            containerFactory = "auditBatchListenerFactory")
    public void onAudit(List<String> messages, Acknowledgment ack) {
        List<AuditRecord> records = new ArrayList<>(messages.size());
        for (String json : messages) {
            try {
                records.add(mapper.readValue(json, AuditRecord.class));
            } catch (Exception e) {
                // Poison pill: skip it rather than failing the whole batch.
                log.error("Skipping unparseable audit record: {}", json, e);
            }
        }

        if (records.isEmpty()) {
            ack.acknowledge();
            return;
        }

        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, AuditRecord.class);
        for (AuditRecord record : records) {
            bulk.replaceOne(
                    Query.query(Criteria.where("_id").is(record.auditId())),
                    record,
                    FindAndReplaceOptions.options().upsert());
        }

        // Single w:majority round-trip for the whole chunk. A failure here throws
        // and is NOT acknowledged, so the batch is redelivered.
        BulkWriteResult result = bulk.execute();
        ack.acknowledge();

        log.debug("Persisted audit batch: received={}, parsed={}, upserts={}, modified={}",
                messages.size(), records.size(), result.getUpserts().size(), result.getModifiedCount());
    }
}
