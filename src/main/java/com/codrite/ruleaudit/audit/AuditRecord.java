package com.codrite.ruleaudit.audit;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Data model for evaluation audit logs stored in MongoDB.
 * <p>
 * Each record represents the evaluation of ONE specific rule against an event.
 * 
 * @param auditId           Deterministic unique ID: {topic}:{partition}:{offset}:{ruleId}.
 * @param ruleId            The ID of the rule being evaluated.
 * @param schemaVersion     Version of the audit record format.
 * @param auditType         Categorization of the result (MATCHED, UNMATCHED, ERRORED).
 * @param reason            Detailed explanation for UNMATCHED or ERRORED states.
 * @param sourceEvent       The original raw JSON input.
 * @param routedEvent       The payload sent to the target topic (present if matched).
 * @param sourceTopic       The Kafka topic where the record originated.
 * @param partition         The Kafka partition.
 * @param offset            The Kafka offset.
 * @param timestamp         Wall-clock time of processing.
 */
@Document(collection = "audits")
@CompoundIndexes({
    @CompoundIndex(name = "ts_rule", def = "{'timestamp': 1, 'ruleId': 1}"),
    @CompoundIndex(name = "topic_part_off", def = "{'sourceTopic': 1, 'partition': 1, 'offset': 1}"),
    // Covers the analytics "messages processed" aggregation: a timestamp-range
    // match followed by a group on (sourceTopic, partition, offset). With all
    // four fields in one index the distinct-message count runs index-only and
    // avoids fetching every document in the range.
    @CompoundIndex(name = "ts_msg", def = "{'timestamp': 1, 'sourceTopic': 1, 'partition': 1, 'offset': 1}")
})
public record AuditRecord(
        @Id String auditId,
        @Indexed String ruleId,
        int schemaVersion,
        AuditType auditType,
        String reason,
        String sourceEvent,
        String routedEvent,
        String sourceTopic,
        int partition,
        long offset,
        @Indexed Instant timestamp,
        long parseTimeNano,
        long evalTimeNano,
        long totalTimeNano) {}
