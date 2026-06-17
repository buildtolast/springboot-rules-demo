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
import org.apache.kafka.streams.processor.api.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class RoutingProcessor implements Processor<String, String, String, RoutingResult> {

    private static final Logger log = LoggerFactory.getLogger(RoutingProcessor.class);

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

    @Override
    public void init(ProcessorContext<String, RoutingResult> context) {
        this.context = context;
    }

    @Override
    public void process(Record<String, String> record) {
        var meta = context.recordMetadata();
        String topic = meta.isPresent() ? meta.get().topic() : "unknown";
        int partition = meta.isPresent() ? meta.get().partition() : 0;
        long offset = meta.isPresent() ? meta.get().offset() : 0L;

        String value = record.value();
        String auditId = AuditKey.of(topic, partition, offset);
        long ts = record.timestamp();

        log.debug("Processing record from topic={}, partition={}, offset={}, auditId={}", topic, partition, offset, auditId);

        try {
            Map<String, Object> root = jsonFactory.toRoot(value);
            EvaluationResult r = evaluator.evaluate(root, cache.get());
            String routed = r.matched() ? value : null;

            if (r.matched()) {
                log.info("Record {} MATCHED {} rules and will be routed", auditId, r.matchedRuleIds().size());
            } else {
                log.debug("Record {} did not match any rules", auditId);
            }

            AuditRecord audit = new AuditRecord(
                auditId, 1, r.verdict(),
                r.matchedRuleIds(), r.evaluatedRuleIds(), r.errors(),
                value, routed, topic, partition, offset, Instant.now()
            );

            context.forward(new Record<>(
                auditId,
                new RoutingResult(r.matched(), routed, toJson(audit)),
                ts
            ));
        } catch (JsonParseException e) {
            log.error("Failed to parse JSON for record {}: {}", auditId, e.getMessage());
            AuditRecord audit = new AuditRecord(
                auditId, 1, AuditType.ERRORED,
                List.of(), List.of(), Map.of("_parse", String.valueOf(e.getMessage())),
                value, null, topic, partition, offset, Instant.now()
            );

            context.forward(new Record<>(
                auditId,
                new RoutingResult(false, null, toJson(audit)),
                ts
            ));
        }
    }

    private String toJson(AuditRecord audit) {
        try {
            return mapper.writeValueAsString(audit);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit record", e);
        }
    }
}
