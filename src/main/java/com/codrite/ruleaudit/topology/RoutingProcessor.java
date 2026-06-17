package com.codrite.ruleaudit.topology;

import com.codrite.ruleaudit.audit.AuditKey;
import com.codrite.ruleaudit.audit.AuditRecord;
import com.codrite.ruleaudit.audit.AuditType;
import com.codrite.ruleaudit.eval.EvaluationResult;
import com.codrite.ruleaudit.eval.RuleEvaluator;
import com.codrite.ruleaudit.json.JsonContextFactory;
import com.codrite.ruleaudit.json.JsonParseException;
import com.codrite.ruleaudit.rules.RuleCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Kafka Streams Processor that evaluates rules against incoming JSON records.
 * <p>
 * For each record, it:
 * <ol>
 *     <li>Generates a deterministic audit ID based on topic, partition, and offset.</li>
 *     <li>Parses the JSON payload into a traversable context.</li>
 *     <li>Evaluates the context against the current set of active rules from {@link RuleCache}.</li>
 *     <li>Creates an {@link AuditRecord} containing the evaluation results (verdict, matched rules, etc.).</li>
 *     <li>Forwards a {@link RoutingResult} containing the match status and serialized audit data.</li>
 * </ol>
 */
@Slf4j
public class RoutingProcessor implements Processor<String, String, String, RoutingResult> {

    private final RuleEvaluator evaluator;
    private final RuleCache cache;
    private final JsonContextFactory jsonFactory;
    private final ObjectMapper mapper;
    private ProcessorContext<String, RoutingResult> context;

    public RoutingProcessor(RuleEvaluator evaluator, RuleCache cache,
                            JsonContextFactory jsonFactory, ObjectMapper mapper) {
        this.evaluator = evaluator;
        this.cache = cache;
        this.jsonFactory = jsonFactory;
        this.mapper = mapper;
    }

    /**
     * Initializes the processor with the provided context.
     * @param context The processor context used to forward results and access metadata.
     */
    @Override
    public void init(ProcessorContext<String, RoutingResult> context) {
        this.context = context;
    }

    /**
     * Processes a single input record from the source Kafka topic.
     * @param record The input record containing the JSON payload.
     */
    @Override
    public void process(Record<String, String> record) {
        // Extract Kafka metadata for auditing and tracing
        var meta = context.recordMetadata();
        String topic = meta.isPresent() ? meta.get().topic() : "unknown";
        int partition = meta.isPresent() ? meta.get().partition() : 0;
        long offset = meta.isPresent() ? meta.get().offset() : 0L;

        String value = record.value();
        // Generate a unique ID for this specific event instance
        String eventId = AuditKey.of(topic, partition, offset);
        long ts = record.timestamp();

        log.debug("Processing record from topic={}, partition={}, offset={}, eventId={}", topic, partition, offset, eventId);

        try {
            long startNano = System.nanoTime();
            // Step 1: Parse the incoming JSON string into a Map structure for SpEL evaluation
            Map<String, Object> root = jsonFactory.toRoot(value);
            long parseEndNano = System.nanoTime();
            
            // Step 2: Evaluate all active rules against the data
            EvaluationResult r = evaluator.evaluate(root, cache.get());
            long evalEndNano = System.nanoTime();
            
            long parseTime = parseEndNano - startNano;
            long evalTime = evalEndNano - parseEndNano;
            long totalTime = evalEndNano - startNano;

            // If any rule matched, we keep the original payload for the target topic
            String routed = r.matched() ? value : null;

            if (r.matched()) {
                log.info("Record {}: Match found and will be routed", eventId);
            } else {
                log.debug("Record {} did not match any rules", eventId);
            }

            // Step 3: Construct individual audit records for each rule evaluation
            List<String> auditJsons = r.ruleResults().stream().map(result -> {
                AuditRecord audit = new AuditRecord(
                    AuditKey.of(topic, partition, offset, result.ruleId()),
                    result.ruleId(),
                    1,
                    result.type(),
                    result.reason(),
                    value,
                    result.type() == AuditType.MATCHED ? value : null,
                    topic,
                    partition,
                    offset,
                    Instant.now(),
                    parseTime,
                    evalTime,
                    totalTime
                );
                return toJson(audit);
            }).collect(java.util.stream.Collectors.toList());

            // If no rules were evaluated (e.g. none active), still record the event
            if (auditJsons.isEmpty()) {
                AuditRecord audit = new AuditRecord(
                    eventId,
                    "_none",
                    1,
                    AuditType.UNMATCHED,
                    "No active rules for evaluation",
                    value,
                    null,
                    topic,
                    partition,
                    offset,
                    Instant.now(),
                    parseTime,
                    evalTime,
                    totalTime
                );
                auditJsons.add(toJson(audit));
            }

            // Step 4: Forward the consolidated result downstream in the DSL topology
            context.forward(new Record<>(
                eventId,
                new RoutingResult(r.matched(), routed, auditJsons),
                ts
            ));
        } catch (JsonParseException e) {
            // Handle parsing failures by creating an error audit record
            log.error("Failed to parse JSON for record {}: {}", eventId, e.getMessage());
            AuditRecord audit = new AuditRecord(
                eventId,
                "_parse",
                1,
                AuditType.ERRORED,
                "JSON parse failure: " + e.getMessage(),
                value,
                null,
                topic,
                partition,
                offset,
                Instant.now(),
                0, 0, 0
            );

            context.forward(new Record<>(
                eventId,
                new RoutingResult(false, null, List.of(toJson(audit))),
                ts
            ));
        }
    }

    /**
     * Serializes an AuditRecord to its JSON representation.
     * @param audit The audit record to serialize.
     * @return A JSON string representing the audit record.
     */
    private String toJson(AuditRecord audit) {
        try {
            return mapper.writeValueAsString(audit);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit record", e);
        }
    }
}
