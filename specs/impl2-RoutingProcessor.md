# GENERATE: src/main/java/com/codrite/ruleaudit/topology/RoutingProcessor.java

Kafka Streams low-level Processor per the contract. Package
com.codrite.ruleaudit.topology. Implements
org.apache.kafka.streams.processor.api.Processor<String,String,String,String>.

Imports include:
  com.codrite.ruleaudit.audit.AuditKey, com.codrite.ruleaudit.audit.AuditRecord,
  com.codrite.ruleaudit.audit.AuditType, com.codrite.ruleaudit.eval.EvaluationResult,
  com.codrite.ruleaudit.eval.RuleEvaluator, com.codrite.ruleaudit.json.JsonContextFactory,
  com.codrite.ruleaudit.json.JsonParseException, com.codrite.ruleaudit.rules.RuleCache,
  com.fasterxml.jackson.databind.ObjectMapper,
  org.apache.kafka.streams.processor.api.Processor,
  org.apache.kafka.streams.processor.api.ProcessorContext,
  org.apache.kafka.streams.processor.api.Record,
  java.time.Instant, java.util.List, java.util.Map.

Public constants (the topology wires sinks with these exact names):
  public static final String TARGET_SINK = "target-sink";
  public static final String AUDIT_SINK  = "audit-sink";

Fields: the four constructor deps (RuleEvaluator evaluator, RuleCache cache,
JsonContextFactory jsonFactory, ObjectMapper mapper) as private final, plus
private ProcessorContext<String,String> context.

Constructor: assign the four deps.

@Override init(ProcessorContext<String,String> context): this.context = context.

@Override process(Record<String,String> record):
  - Determine provenance from context.recordMetadata():
        var meta = context.recordMetadata();
        String topic = meta.isPresent() ? meta.get().topic() : "unknown";
        int partition = meta.isPresent() ? meta.get().partition() : 0;
        long offset   = meta.isPresent() ? meta.get().offset() : 0L;
  - String value = record.value();
  - String auditId = AuditKey.of(topic, partition, offset);
  - long ts = record.timestamp();
  - try:
        Map<String,Object> root = jsonFactory.toRoot(value);
        EvaluationResult r = evaluator.evaluate(root, cache.get());
        String routed = r.matched() ? value : null;
        AuditRecord audit = new AuditRecord(auditId, 1, r.verdict(),
            r.matchedRuleIds(), r.evaluatedRuleIds(), r.errors(),
            value, routed, topic, partition, offset, Instant.now());
        if (r.matched()) {
            context.forward(new Record<>(auditId, value, ts), TARGET_SINK);
        }
        context.forward(new Record<>(auditId, toJson(audit), ts), AUDIT_SINK);
    catch (JsonParseException e):
        AuditRecord audit = new AuditRecord(auditId, 1, AuditType.ERRORED,
            List.of(), List.of(), Map.of("_parse", String.valueOf(e.getMessage())),
            value, null, topic, partition, offset, Instant.now());
        context.forward(new Record<>(auditId, toJson(audit), ts), AUDIT_SINK);

private String toJson(AuditRecord audit): use mapper.writeValueAsString(audit);
  catch com.fasterxml.jackson.core.JsonProcessingException and rethrow as
  new IllegalStateException("Failed to serialize audit record", e).

Output exactly one top-level class.
