# GENERATE: src/main/java/com/codrite/ruleaudit/topology/RoutingProcessor.java

Refactor for the StreamsBuilder DSL. This is now a Processor that emits a single
RoutingResult per input (the DSL splits it into target/audit downstream). It no
longer forwards to named sinks. Package com.codrite.ruleaudit.topology.

Implements org.apache.kafka.streams.processor.api.Processor<String,String,String,RoutingResult>.

RoutingResult (sibling, already exists, do NOT declare):
  public record RoutingResult(boolean matched, String routedValue, String auditJson) {}

Imports include: com.codrite.ruleaudit.audit.AuditKey, .AuditRecord, .AuditType,
com.codrite.ruleaudit.eval.EvaluationResult, .RuleEvaluator,
com.codrite.ruleaudit.json.JsonContextFactory, .JsonParseException,
com.codrite.ruleaudit.rules.RuleCache, com.fasterxml.jackson.databind.ObjectMapper,
org.apache.kafka.streams.processor.api.{Processor,ProcessorContext,Record},
java.time.Instant, java.util.{List,Map}.

Fields: private final RuleEvaluator evaluator; private final RuleCache cache;
private final JsonContextFactory jsonFactory; private final ObjectMapper mapper;
private ProcessorContext<String, RoutingResult> context;

Constructor assigns the four deps. (No sink-name constants.)

@Override init(ProcessorContext<String, RoutingResult> context): this.context = context.

@Override process(Record<String,String> record):
  - var meta = context.recordMetadata();
    String topic = meta.isPresent() ? meta.get().topic() : "unknown";
    int partition = meta.isPresent() ? meta.get().partition() : 0;
    long offset = meta.isPresent() ? meta.get().offset() : 0L;
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
        context.forward(new Record<>(auditId,
            new RoutingResult(r.matched(), routed, toJson(audit)), ts));
    catch (JsonParseException e):
        AuditRecord audit = new AuditRecord(auditId, 1, AuditType.ERRORED,
            List.of(), List.of(), Map.of("_parse", String.valueOf(e.getMessage())),
            value, null, topic, partition, offset, Instant.now());
        context.forward(new Record<>(auditId,
            new RoutingResult(false, null, toJson(audit)), ts));

private String toJson(AuditRecord audit): mapper.writeValueAsString(audit), catch
com.fasterxml.jackson.core.JsonProcessingException -> throw new
IllegalStateException("Failed to serialize audit record", e).

Output exactly one top-level class.
