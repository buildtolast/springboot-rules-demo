package com.codrite.ruleaudit.audit;

import java.time.Instant;
import org.springframework.data.annotation.Id;
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
public record AuditRecord(
        @Id String auditId,
        String ruleId,
        int schemaVersion,
        AuditType auditType,
        String reason,
        String sourceEvent,
        String routedEvent,
        String sourceTopic,
        int partition,
        long offset,
        Instant timestamp,
        long parseTimeNano,
        long evalTimeNano,
        long totalTimeNano) {}
